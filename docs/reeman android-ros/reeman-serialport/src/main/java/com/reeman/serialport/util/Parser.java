package com.reeman.serialport.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Parser {

    public static String checkXor(String data) {
        int checkData = 0;
        for (int i = 0; i < data.length(); i = i + 2) {
            int start = Integer.parseInt(data.substring(i, i + 2), 16);
            checkData = start ^ checkData;
        }
        String ss = Integer.toHexString(checkData);
        if (ss.length() % 2 != 0) {
            ss = "0" + ss;
        }
        return ss.toUpperCase();
    }


    public static String byte2Hex(Byte inByte) {
        return String.format("%02x", inByte).toUpperCase();
    }

    public static String byteArrToHex(byte[] inBytArr, int len) {
        StringBuilder strBuilder = new StringBuilder();

        for (int i = 0; i < len; ++i) {
            strBuilder.append(byte2Hex(inBytArr[i]));
            strBuilder.append("");
        }

        return strBuilder.toString();
    }

    public static String hexStringToString(String s) {
        if (s == null || s.equals("")) {
            return null;
        }
        s = s.replace(" ", "");
        byte[] baKeyword = new byte[s.length() / 2];
        for (int i = 0; i < baKeyword.length; i++) {
            try {
                int parseInt = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
                if (parseInt == 0) {
                    baKeyword[i] = 32;
                } else {
                    baKeyword[i] = (byte) (0xff & parseInt);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            s = new String(baKeyword, StandardCharsets.UTF_8);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        return s;
    }

    public static byte[] string2BH(String res) {
        byte[] bytes = res.getBytes();
        byte[] byte1 = new byte[4 + bytes.length];
        byte1[byte1.length - 1] = (byte) bytes.length;

        for (int i = 0; i < bytes.length; ++i) {
            byte1[byte1.length - 1] ^= bytes[i];
            byte1[i + 3] = bytes[i];
        }
        byte1[0] = -86;
        byte1[1] = 84;
        byte1[2] = (byte) bytes.length;
        return byte1;
    }

    public static String hexString2BH(String res) {
        res = res + "00";
        byte[] bytes = new byte[res.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(res.substring(i * 2, i * 2 + 2), 16);
        }
        return Arrays.toString(bytes).replace("[", "").replace("]", "").replace(",", "") + "len:" + bytes.length;
    }

    public static String byteArrayToDecimalString(byte[] byteArray) {
        StringBuilder sb = new StringBuilder();
        for (byte b : byteArray) {
            int decimalValue = b & 0xFF;
            sb.append(decimalValue).append(" ");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

}