package com.jimmy.serial;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.jimmy.printer.R;
import com.jimmy.serial.utils.SerialPort;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.security.InvalidParameterException;
import java.util.Arrays;

public class SerialPortRead {

    private SerialPort serialPort;
    private InputStream is;
    private boolean isReading = false;
    private ReadCallback callback;
    private MyHandler myHandler;

    public void open(String path, int baudRate, ReadCallback callback) {
        try {
            this.myHandler = new MyHandler(this);
            this.callback = callback;
            this.isReading = true;
            this.serialPort = new SerialPort(new File(path), baudRate, 0);
            this.is = serialPort.getInputStream();
            new ReadThread(is).start();
        } catch (SecurityException e) {
            callback.onReading("SecurityException");
            //DisplayError(R.string.error_security);
        } catch (IOException e) {
            callback.onReading("IOException");
            //DisplayError(R.string.error_unknown);
        } catch (InvalidParameterException e) {
            callback.onReading("InvalidParameterException");
            //DisplayError(R.string.error_configuration);
        }
    }

    private class ReadThread extends Thread {

        private InputStream is;

        public ReadThread(InputStream is) {
            this.is = is;
        }

        @Override
        public void run() {
            while (isReading) {
                long id = Thread.currentThread().getId();
                Log.d("thread", "id =" + id);
                try {
                    if (is == null) return;
                    String s;
                    byte[] line = readLine(is);
                    if (line[0] == 87) {
                        s = readWENTEC(line);
                    } else {
                        s = readDaHua(line);
                    }
                    sendMessage(s);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String readDaHua(byte[] line) {
        try {
            String s = ASCII2HexString(line, 22).substring(0, 5).trim();
            double b = Double.parseDouble(s) / 1000;
            return String.valueOf(b);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return "0.000";
    }

    private byte[] readLine(InputStream is) throws IOException {
        //5518    000     000\n\r
        //WGT:1 5.570P 0.000\r\n

        byte[] line = new byte[22];
        byte[] buffer = new byte[2];
        int index = 0;
        byte[] _r = new byte[1];//13
        byte[] _n = new byte[1];//10
        for (; ; ) {
            if (is.read(buffer, 0, 1) != -1) {
                line[index] = buffer[0];
                index++;

                if (buffer[0] == 13) {
                    _r[0] = buffer[0];
                }

                if (buffer[0] == 10) {
                    _n[0] = buffer[0];
                }

                boolean isEnd = _r[0] == 13 && _n[0] == 10;
                if (index >= 22 || isEnd) {
                    break;
                }
            } else {
                break;
            }
        }
        Log.d("byteline", Arrays.toString(line));
        return line;
    }

    private String readWENTEC(byte[] line) throws IOException {
        Log.d("line=", Arrays.toString(line));

        byte[] buf = new byte[20];
        if (line[0] == 'W') {
            byte status = line[4];
            byte[] net_weight = new byte[7];
            byte sep = 'P';
            byte[] tare_weight = new byte[7];
            System.arraycopy(line, 5, net_weight, 0, 6);
            System.arraycopy(line, 12, tare_weight, 0, 6);
            buf[0] = status;
            for (int i = 0; i < 6; i++) {
                buf[i + 1] = net_weight[i];
            }
            buf[7] = sep;
            for (int j = 0; j < 6; j++) {
                buf[j + 8] = tare_weight[j];
            }
        }
        Log.d("WINTEC：", ASCII2HexString(line, line.length));
        String a = ASCII2HexString(buf, 20).substring(0, 1);
        String b = ASCII2HexString(buf, 20).substring(1, 7);
        String c = ASCII2HexString(buf, 20).substring(8, 14);
        Log.d("WINTEC：", "a=" + a + ", b=" + b + ", c=" + c);
        return b;
    }

    public String ASCII2HexString(byte[] src, int n) {
        char[] data = new char[n];
        for (int i = 0; i < src.length; i++) {
            data[i] = (char) ((int) (src[i]));
        }
        return new String(data);
    }

    private static class MyHandler extends Handler {

        private WeakReference<SerialPortRead> weakReference;

        public MyHandler(SerialPortRead read) {
            this.weakReference = new WeakReference<>(read);
        }

        @Override
        public void handleMessage(Message msg) {
            SerialPortRead read = weakReference.get();
            if (read != null && read.callback != null) {
                read.callback.onReading((String) msg.obj);
            }
        }
    }

    private void sendMessage(String s) {
        if (myHandler == null) return;
        Message msg = new Message();
        msg.obj = s;
        myHandler.sendMessage(msg);
    }

    public void close() {
        isReading = false;
        try {
            if (is != null) {
                is.close();
            }
            if (serialPort != null) {
                serialPort.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
