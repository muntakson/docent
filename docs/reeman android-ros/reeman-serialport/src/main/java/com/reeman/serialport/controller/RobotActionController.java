package com.reeman.serialport.controller;

import android.os.Build;
import android.text.TextUtils;

import com.reeman.serialport.BuildConfig;
import com.reeman.serialport.util.LogUtils;
import com.reeman.serialport.util.NetworkUtil;
import com.reeman.serialport.util.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;


public class RobotActionController {

    private static RobotActionController INSTANCE;
    private RosCallbackParser parser;
    private String ipAddress;
    private List<String> pathList;
    private ScheduledExecutorService scheduledExecutorService;

    /**
     * 设置ROS ip地址,以将日志上传到ROS
     *
     * @param ipAddress
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public static RobotActionController getInstance() {
        if (INSTANCE == null) {
            synchronized (RobotActionController.class) {
                if (INSTANCE == null) {
                    INSTANCE = new RobotActionController();
                }
            }
        }
        return INSTANCE;
    }


    /**
     * 默认的初始化串口方法,波特率:115200,串口地址:/dev/ttyS1
     *
     * @param callback ROS上报内容的回调
     * @param path     要上传到ros的日志目录
     * @throws Exception
     */
    public void init(RosCallbackParser.RosCallback callback, String... path) throws Exception {
        parser = new RosCallbackParser.Builder()
                .baudRate(115200)
                .port("/dev/ttyS1")
                .callback(callback)
                .build();
        startListen(path);
    }


    /**
     * @param baudRate 波特率
     * @param port     串口地址
     * @param callback ROS上报内容的回调
     * @param path     要上传到ros的日志目录
     * @throws Exception
     */
    public void init(int baudRate, String port, RosCallbackParser.RosCallback callback, String... path) throws Exception {
        parser = new RosCallbackParser.Builder()
                .baudRate(baudRate)
                .port(port)
                .callback(callback)
                .build();
        Timber.tag(BuildConfig.LOG_ROS).d("baudRate: " + baudRate + ",port: " + port);
        startListen(path);
    }

    private void startListen(String... path) throws Exception {
        parser.startListen();
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleWithFixedDelay(task, 10, 60, TimeUnit.SECONDS);
        pathList = new ArrayList<>();
        if (path != null && path.length != 0) {
            pathList.addAll(Arrays.asList(path));
        }
        if (Build.PRODUCT.startsWith("rk312x")) {
            try {
                PowerBoardReceiver.getInstance().start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stopListen() {
        scheduledExecutorService.shutdownNow();
        PowerBoardReceiver.getInstance().stop();
        if (parser != null) {
            parser.stopListen();
            parser = null;
        }
        INSTANCE = null;
    }

    Runnable task = () -> {
        try {
            if (TextUtils.isEmpty(ipAddress) || "127.0.0.1".equals(ipAddress) || !NetworkUtil.isHostReachable(ipAddress, 1000) || pathList == null)
                return;
            LogUtils.uploadLogs(ipAddress, pathList);
        } catch (Exception e) {

        }

    };

    /**
     * 发送指令到导航,异步返回结果
     *
     * @param command
     * @return 返回结果在RobotActionController.onResult(String)
     * @see com.reeman.serialport.controller.RosCallbackParser.RosCallback#(String)
     */
    public void sendCommand(String command) {
        parser.sendCommand(command);
        if (!command.startsWith("keep") && !command.startsWith("send_to_base") && !command.startsWith("get_battery_info"))
            Timber.tag(BuildConfig.LOG_ROS).v("send %s", command);
    }

    public void sendCommandToQueue(String command) {
        parser.sendCommandToQueue(command);
        Timber.tag(BuildConfig.LOG_ROS).v("send2 %s", command);
    }

    /**
     * 控制临时停靠开关
     *
     * @param open
     */
    public void setTolerance(boolean open) {
        sendCommand("set_tolerance[" + (open ? "1]" : "0]"));
    }


    public void getSpecialArea() {
        sendCommand("sendWeb[in_polygon]");
    }

    /**
     * 不规划局部路径,直接去目标点
     *
     * @param x
     * @param y
     * @param z
     */
    public void listPoint(double x, double y, double z) {
        sendCommand("list_point[" + x + "," + y + "," + z + "]");
    }

    /**
     * 取临停点
     */
    public void getNearest() {
        sendCommand("get_nearest");
    }

    /**
     * 心跳
     *
     * @return hfls_version:HardwareVersion FirmwareVersion LoaderVersion SoftVersion
     */
    public void heartBeat() {
        sendCommand("keep_connect");
    }

    /**
     * 获取导航模式
     *
     * @return model:x
     * x: 1:导航模式;2:建图模式;3:增量建图模式;
     */
    public void modelRequest() {
        sendCommand("model:request");
    }


    /**
     * 设置最大导航速度
     *
     * @param maxSpeed range [0.3 - 1.0]
     * @return get_max_vel:x
     * x: 当前最大速度
     */
    public void setNavSpeed(String maxSpeed) {
        Float speed = Float.parseFloat(maxSpeed);
        if (speed.compareTo(0.3F) < 0) speed = 0.3F;
        if (speed.compareTo(1.0F) > 0) speed = 1.0F;
        sendCommand("max_vel[" + speed + "]");
    }

    /**
     * 获取最大导航速度
     *
     * @return get_max_vel:x
     * x: 当前最大速度
     */
    public void getNavSpeed() {
        sendCommand("get_max_vel");
    }

    /**
     * 设置CPU高性能模式
     */
    public void cpuPerformance() {
        sendCommand("cpu_performance");
    }

    /**
     * 根据坐标和线速度设置其他机器人的位置,从而实现多机避障
     *
     * @param x
     * @param y
     * @param z
     * @param hostname 其他机器人名称
     * @param speed    线速度
     */
    public void expand(double x, double y, double z, String hostname, double speed) {
        sendCommand("robot_cost[" + x + "," + y + "," + z + "," + (speed == 0.0f ? 0.5 : speed) + "," + hostname + ",0.006]");
    }


    public void expand(double x, double y, double z, String hostname, double speed, double cover) {
        String c = "robot_cost[" + x + "," + y + "," + z + "," + (speed == 0.0f ? 0.5 : speed) + "," + hostname + "," + cover + "]";
        sendCommand(c);
    }

    /**
     * 整机关机指令
     * - 发送整机关机指令后15s电源板会断电
     * - 建议在发送完整机关机指令后给安卓发送关机指令
     */
    public void shutdown() {
        sendCommand("power_off");
    }

    /**
     * 进入loader模式
     *
     * @deprecated web端已支持升级电源板, 不要在app进入loader模式
     */
    @Deprecated
    public void upgradeLoading() {
        sendCommand("base_upgrade[start]");
    }

    /**
     * 开始升级
     *
     * @deprecated web端已支持升级电源板, 不要在app进入loader模式
     */
    @Deprecated
    public void affirmUpgrade() {
        sendCommand("base_upgrade[affirm]");
    }

    /**
     * 退出loader模式
     *
     * @deprecated web端已支持升级电源板, 不要在app进入loader模式
     */
    @Deprecated
    public void cancelIap() {
        sendCommand("cancel_iap");
    }

    /**
     * 获取当前速度,当前遇到障碍物停留时间,全局路径是否考虑临时障碍
     */
    public void updateDynamic() {
        sendCommand("update_dynamic");
    }

    /**
     * 写入最大速度(0.3-1.0)
     *
     * @param maxSpeed range [0.3 - 1.0]
     * @return get_max_vel:x
     * x: 当前最大速度
     */
    public void writeMaxValue(String maxSpeed) {
        Float speed = Float.parseFloat(maxSpeed);
        if (speed.compareTo(0.3F) < 0) speed = 0.3F;
        if (speed.compareTo(1.0F) > 0) speed = 1.0F;
        sendCommand("write_max_vel[" + speed + "]");
    }

    /**
     * 设置导航中遇到障碍物停留时间
     *
     * @param stopTime range [1 - 10]
     * @return get_stop_time:5.0
     */
    public void setStopTime(int stopTime) {
        if (stopTime < 1) stopTime = 1;
        if (stopTime > 10) stopTime = 10;
        sendCommand("set_stop_time[" + stopTime + "]");
    }

    /**
     * 全局路径是否考虑临时障碍
     *
     * @param consider 是否考虑
     * @return get_global_p:1.0
     */
    public void globalTemporaryObstacleControl(boolean consider) {
        sendCommand("set_globalcost_p[" + (consider ? 1 : 0) + "]");
    }

    /**
     * 整机重启
     */
    public void powerReboot() {
        sendCommand("power_reboot");
    }

    /**
     * 重启导航系统
     *
     * @return initpose:0,x y radian
     */
    public void sysReboot() {
        sendCommand("sys:reboot");
    }

    /**
     * 激光数据上报控制
     *
     * @param report true:打开;false:关闭
     * @return laser[distance]
     */
    public void lidarReportControl(boolean report) {
        sendCommand("switch_lidar[" + (report ? "on]" : "off]"));
    }

    /**
     * 获取导航主机编号
     *
     * @return sys:boot:x
     * x: 导航主机编号
     */
    public void getHostName() {
        sendCommand("hostname:get");
    }

    /**
     * 获取导航主机版本
     *
     * @return ver:x
     * x: 导航版本
     * @deprecated 心跳包会上报导航主机版本, 推荐从心跳包获取
     */
    @Deprecated
    public void getHostVersion() {
        sendCommand("sys:version");
    }

    /**
     * 获取导航wifi和ip
     *
     * @return ip:ssid:x.x.x.x
     * ssid: wifi名
     * x.x.x.x:ip地址
     */
    public void getHostIp() {
        sendCommand("ip:request");
    }

    /**
     * 获取当前地图
     *
     * @return current_map[map_name:x]
     * x: 地图名称
     */
    public void getCurrentMap() {
        sendCommand("nav:current_map");
    }

    /**
     * 标点
     *
     * @param arr   坐标
     * @param type  类型
     * @param point 名称
     */
    public void markPoint(double[] arr, String type, String point) {
        if (arr == null || arr.length != 3)
            return;
        sendCommand("nav:set_flag_point[" + arr[0] + "," + arr[1] + "," + arr[2] + "," + type + "," + point + "]");
    }

    /**
     * 对接充电桩
     */
    public void dockStart() {
        sendCommand("dock:start");
    }

    /**
     * 取消对接充电桩
     */
    public void cancelCharge() {
        sendCommand("dock:stop");
    }


    /**
     * 删除指定点位
     *
     * @param name
     */
    public void deletePoint(String name) {
        sendCommand("nav:del_flag_point[" + name + "]");
    }

    /**
     * 保存地图
     */
    public void saveMap() {
        sendCommand("save_map");
    }

    /**
     * 切换到导航模式
     */
    public void modelNavi() {
        sendCommand("model:navi");
    }

    /**
     * 切换到建图模式
     */
    public void modelMapping() {
        sendCommand("model:mapping");
    }

    /**
     * 切换到增量建图模式
     */
    public void modelRemap() {
        sendCommand("model:remap");
    }

    /**
     * 导航到点
     * 如果点的type为`normal`,则为普通导航;
     * 如果点的type为`charge`,导航到目标点后会自动对接充电桩;
     *
     * @param point
     * @return nav_result{state code name dist_to_goal mileage}
     */
    public void navigationByPoint(String point) {
        String cmd = "nav_point[" + point + "]";
        sendCommand(cmd);
    }

    /**
     * 获取电池固定信息
     *
     * @return battery_info{manufacturer nominal_voltage temperature cycle_times rated_capacity full_capacity capacity}
     */
    public void getBatteryInfo() {
        sendCommand("get_battery_info");
    }


    /**
     * 电池动态信息上报控制
     *
     * @param report
     * @return current_info{55 202 0 0 1}
     */
    public void currentInfoControl(boolean report) {
        sendCommand("get_current_info[" + (report ? 1 : 0) + "]");
    }

    /**
     * 切换地图
     *
     * @param mapName 地图名称
     * @return apply_map[map_name:m]
     */
    public void applyMap(String mapName) {
        sendCommand("call_web[apply_map:" + mapName + "]");
    }

    /**
     * 坐标导航
     *
     * @param x      x轴坐标
     * @param y      y轴坐标
     * @param radian 弧度
     * @return nav_result{state code name dist_to_goal mileage}
     */
    public void navigationByCoordinates(String x, String y, String radian) {
        String cmd = "goal:nav[" + x + "," + y + "," + radian + "]";
        sendCommand(cmd);
    }

    /**
     * 坐标导航
     *
     * @param x      x轴坐标
     * @param y      y轴坐标
     * @param radian 弧度
     * @return nav_result{state code name dist_to_goal mileage}
     */
    public void navigationByCoordinates(double x, double y, double radian) {
        String cmd = "goal:nav[" + x + "," + y + "," + radian + "]";
        sendCommand(cmd);
    }

    /**
     * 暂停导航
     *
     * @return nav_result{2 code name dist_to_goal mileage}
     */
    public void pauseNavigation() {
        sendCommand("nav_pause");
    }

    /**
     * 恢复导航
     *
     * @return nav_result{state code name dist_to_goal mileage}
     */
    public void resumeNavigation() {
        sendCommand("nav_resume");
    }

    /**
     * 获取点位坐标
     *
     * @param point 点位名称
     *              return get_flag_point[x,y,radian,type,name]/get_flag_point:-1
     */
    public void getPointPosition(String point) {
        sendCommand("nav:get_flag_point[" + point + "]");
    }

    /**
     * 获取当前位置坐标
     *
     * @return pose[x, y, radian]/pose:notfound
     */
    public void getCurrentPosition() {
        sendCommand("nav:get_pose");
    }

    /**
     * 重定位
     *
     * @param coordinate 坐标
     * @return initpose:0,x y radian
     */
    public void relocateByCoordinate(double[] coordinate) {
        if (coordinate == null || coordinate.length != 3)
            return;
        sendCommand("nav:reloc[" + coordinate[0] + "," + coordinate[1] + "," + coordinate[2] + "]");
    }

    public void relocByName(String point) {
        sendCommand("nav:reloc_name[" + point + "]");
    }

    public void moveRight(int angle, int speed) {
        sendCommand("move[" + angle + "," + speed + "]");
    }

    public void moveForward() {
        sendCommand("move[100,0]");
    }

    public void moveBackward() {
        sendCommand("move[-100,0]");
    }

    public void turn(double angle) {
        sendCommand("move[0," + angle + "]");
    }

    public void stopMove() {
        sendCommand("move[0,0]");
    }

    /**
     * 取消导航
     *
     * @return nav_result{4 code name dist_to_goal mileage}
     */
    public void cancelNavigation() {
        sendCommand("nav_cancel");
    }


    /**
     * ROS连接wifi
     *
     * @param wifiName     wifi名称
     * @param wifiPassword wifi密码
     * @return wifi:connect success 连接成功
     * wifi:connect fail 连接失败
     * wifi:connecting 连接进行中
     */
    public void connectROSWifi(String wifiName, String wifiPassword) {
        sendCommand("wifi[ssid " + wifiName + ";pwd " + wifiPassword + "]");
    }

    /**
     * 自动上报坐标
     *
     * @param autoUpload
     */
    public void positionAutoUploadControl(boolean autoUpload) {
        sendCommand("nav:get_pose[" + (autoUpload ? "on" : "off") + "]");
    }

    /**
     * 发送透传指令到电源板
     *
     * @param data 数据位
     */
    public void sendToBase(int... data) {
        int length = data.length + 2;
        byte[] bytes = new byte[length];
        bytes[0] = (byte) 0XD0;
        bytes[1] = (byte) data.length;
        for (int i = 2; i < length; i++) {
            bytes[i] = (byte) data[i - 2];
        }
        String s = Parser.byteArrayToDecimalString(bytes);
        Timber.tag(BuildConfig.LOG_ROS).v("透传 %s", s);
        sendToBase(s);
    }

    /**
     * 发送透传指令
     *
     * @param data
     */
    public void sendToBase(String data) {
        sendCommand("send_to_base[" + data + "]");
    }
}
