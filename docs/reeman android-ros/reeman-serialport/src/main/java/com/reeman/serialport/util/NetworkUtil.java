package com.reeman.serialport.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class NetworkUtil {

    public static boolean isHostReachable(String hostName, int timeout) {
        try {
            InetAddress address = InetAddress.getByName(hostName);
            return address.isReachable(timeout);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
