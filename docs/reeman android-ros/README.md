# android与ROS串口通讯示例说明

本示例中串口解析部分fork自Google串口通信库,在其基础上进行封装,根据reeman android-ros串口协议做了完整的收发方法,并提供了部分常用指令的示例;

本实例中使用[com.jakewharton.timber](https://github.com/JakeWharton/timber)和[com.elvishew:xlog](https://github.com/elvishew/xLog)封装了一套日志库,具体使用方式参考示例代码及以上两个库的官方说明;

使用:

- 在项目根build.gradle中添加:

````groovy
pluginManagement {
    repositories {
        //...
        maven { url 'https://jitpack.io' }
    }
}
````
- 在app/module目录build.gradle中添加:

````groovy
dependencies {
    implementation  'com.github.Misaka-XXXXII:reeman-lib:1.1.0'
    implementation  'com.github.Misaka-XXXXII.reeman-lib:serialport:1.1.0'
}
````

- 初始化

```java
XLog.init();
Timber.plant(new FileLoggingTree(
                Log.VERBOSE,//打印的日志级别
                BuildConfig.DEBUG,//是否将日志打印到控制台
                Environment.getExternalStorageDirectory().getPath(),//日志根目录
                BuildConfig.APP_LOG_DIR,//默认tag
                Arrays.asList(BuildConfig.APP_LOG_DIR,//具体的日志类别
                    com.reeman.serialport.BuildConfig.LOG_POWER_BOARD
                )
        )
);
RobotActionController.getInstance().init(
	115200,//波特率
	"/dev/ttyS1",//串口地址,根据实际情况填写
	new RosCallbackParser.RosCallback() {//ros上报数据
        @Override
        public void onResult(String result) {
            //不要在该线程做耗时操作
        }
    },
	BuildConfig.APP_LOG_DIR//需要定时上传到ROS的日志
);
```

- 发送指令

```java
//获取ROS主机名
RobotActionController.getInstance().getHostName();
```

- 释放串口

```java
RobotActionController.getInstance().stopListen();
```

