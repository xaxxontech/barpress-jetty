package innovationBartendr;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Created by colin on 30/01/18.
 */
public class ArduinoPort implements SerialPortEventListener {

    private byte[] buffer = new byte[256];
    private static SerialPort serialPort = null;
    private int buffSize = -1;
    private String portname = "/dev/ttyUSB0";
    private static final int DEVICERESETDELAY = 2000;
    private static final int DEVICEHANDSHAKEDELAY = 500;
    private static final String FIRMWARE_ID = "energybarpress";
    public boolean connected = false;
    private AppServlet app;

    public String actuatorDirection = "unknown";
    public String currentForce = "unknown";
    public String forceTarget = "unknown";
    public String actuatorPos = "unknown";
    public String ambientTemp = "unknown";
    public String upperTemp = "unknown";
    public String upperSetpoint = "unknown";
    public String tempControlActive = "unknown";
    public String lowerTemp = "unknown";
    public String lowerSetpoint = "unknown";
    public String fan = "unknown";

    public static final double BARSQIN = 6.75;
    private volatile boolean commandlock = false;
    private volatile List<Byte> commandList = new ArrayList<>();

    // comands
    public static final byte GET_PRODUCT = 'x';
    public static final byte ZERO = 'z';
    public static final byte LOWERSETPOINT = '5';
    public static final byte UPPERSETPOINT = '4';
    public static final byte FORCETARGET = 'l';
    public static final byte UP = 'u';
    public static final byte STOP = 's';
    public static final byte AUTODOWN = 'g';
    public static final byte AUTODOWNSLOW = 'j';
    public static final byte FANON = 'f';
    public static final byte FANOFF = 'o';
    public static final byte AUTOTEMPLOWER = 'b';
    public static final byte AUTOTEMPUPPER = 'a';
    public static final byte AUTOTEMPOFF = 'n';


    public ArduinoPort(AppServlet a) {
        app = a;
        new CommandSender().start();
    }

    private void execute(int bufferSize) {
        String response = "";
        for (int i = 0; i < bufferSize; i++)
            response += (char) buffer[i];

//        app.printlog(response);

        String s[] = response.split(" ");
//        <STOP 0.00 100.00 0 18.37 18.25 150.00 NO 18.75 150.00 OFF>

        if (s.length != 11) return;

        actuatorDirection = s[0];
        currentForce = convertToPSI(s[1])+" psi / "+s[1]+" lbs";
        forceTarget = convertToPSI(s[2])+" psi / "+s[2]+" lbs";
        actuatorPos = s[3];
        ambientTemp = celsiusToFahrenheit(s[4])+"&deg;F &nbsp; ["+s[4]+" &deg;C]";
        upperTemp = celsiusToFahrenheit(s[5])+"&deg;F &nbsp; ["+s[5]+" &deg;C]";
        upperSetpoint = celsiusToFahrenheit(s[6])+"&deg;F &nbsp; ["+s[6]+" &deg;C]";
        tempControlActive = s[7];
        lowerTemp = celsiusToFahrenheit(s[8])+"&deg;F &nbsp; ["+s[8]+" &deg;C]";
        lowerSetpoint = celsiusToFahrenheit(s[9])+"&deg;F &nbsp; ["+s[9]+" &deg;C]";
        fan = s[10];

        if (actuatorDirection.equals("DOWN")) actuatorDirection="<b>DOWN</b>";
        if (actuatorDirection.equals("UP")) actuatorDirection="<b>UP</b>";
        if (tempControlActive.equals("YES")) tempControlActive="<b>YES</b>";
        if (fan.equals("ON")) fan="<b>ON</b>";

    }

    public boolean connect() {
        if (connected) return false;
        try {
            serialPort = new SerialPort(portname);
            serialPort.openPort();
            serialPort.setParams(115200, 8, 1, 0);
            Thread.sleep(DEVICERESETDELAY);

            serialPort.readBytes(); // clear serial buffer
            serialPort.writeBytes(new byte[]{GET_PRODUCT, 13}); // query device
            Thread.sleep(DEVICEHANDSHAKEDELAY); // some delay is required
            byte[] buffer = serialPort.readBytes();

            app.printlog("attempting connect to device");

            if (buffer == null) { // no response, move on to next port
                serialPort.closePort();
                app.printlog("no response from serial port");
                return false;
            }

            String device = new String();

            for (int n=0; n<buffer.length; n++) {
                if((int)buffer[n] == 13 || (int)buffer[n] == 10) { break; }
                if(Character.isLetterOrDigit((char) buffer[n]))
                    device += (char) buffer[n];
            }

            if (device.length() == 0) {
                app.printlog("serial port no response to get ID");
                serialPort.closePort();
                return false;
            }


            if (device.trim().startsWith("id")) device = device.substring(2, device.length());

            if (device.equals(FIRMWARE_ID)) {
                app.printlog("connected to "+FIRMWARE_ID+" on port: "+portname);
                serialPort.addEventListener(this, SerialPort.MASK_RXCHAR);//Add SerialPortEventListener
                connected = true;
                return true;
            }

            app.printlog("unable to connect, device ID returned: "+device);
            serialPort.closePort();

            return false;

        } catch (Exception e) {e.printStackTrace();  }
        app.printlog("unable to connect to serial port, unknown reason");
        return false;
    }

    public void disconnect() {
        if (!connected) return;
        try {
            connected = false;
            serialPort.closePort();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void serialEvent(SerialPortEvent event) {
        if (!event.isRXCHAR())  return;

        try {
            byte[] input = new byte[32];

            if(serialPort == null){
                app.printlog("serial port is null");
                return;
            }

            input = serialPort.readBytes();
            for (int j = 0; j < input.length; j++) {
                if (input[j] == '>') {
                    if (buffSize > 0) {
                        execute(buffSize);
                        buffSize = -1;
                    }
                    buffSize = 0; // reset

                }else if (input[j] == '<') {  // start of message
                    buffSize = 0;
                } else if (buffSize != -1) {
                    buffer[buffSize++] = input[j];   // buffer until ready to parse
                }
            }

        } catch (SerialPortException e) { e.printStackTrace(); }
    }

    private String celsiusToFahrenheit(String temp) {
        double c = Double.valueOf(temp);
        double f = c * 1.8 + 32;
//        return String.valueOf(f);
        return String.format("%.1f", f);
    }

    private String convertToPSI(String lbs) {
        double psi = Double.valueOf(lbs)/BARSQIN;
//        return String.valueOf(psi);
        return String.format("%.1f", psi);
    }

    public void sendCommand(byte[] cmd) {
        if (!connected) return;

        String text = "sendCommand(): " + (char)cmd[0] + " ";
        for(int i = 1 ; i < cmd.length ; i++)
            text += ((byte)cmd[i] & 0xFF) + " ";   // & 0xFF converts to unsigned byte

        app.printlog(text);

        int n = 0;
        while (commandlock) {
            try { Thread.sleep(1); } catch (Exception e) { e.printStackTrace(); }
            n++;
        }

        commandlock = true;
        for (byte b : cmd) commandList.add(b);
        commandList.add((byte) 13); // EOL
        commandlock = false;

    }

    public void sendCommand(final byte cmd){
        sendCommand(new byte[]{cmd});
    }

    /** inner class to send commands to port in sequential order */
    public class CommandSender extends Thread {

        public CommandSender() {
            this.setDaemon(true);
        }

        public void run() {

            while (true) {
                if (commandList.size() > 0 &! commandlock) {

                    if (commandList.size() > 15) {
                        commandList.clear();
                        app.printlog("error, command stack up, all dropped");
                        try { Thread.sleep(1); } catch (Exception e) { e.printStackTrace(); }
                        continue;
                    }

                    int EOLindex = commandList.indexOf((byte) 13);
                    if (EOLindex == -1) {
                        try { Thread.sleep(1); } catch (Exception e) { e.printStackTrace(); }
                        continue;
                    }


                    // in case of multiple EOL chars, read only up to 1st
                    byte c[] = new byte[EOLindex+1];
                    try {
                        for (int i = 0; i <= EOLindex; i++) {
                            c[i] = commandList.get(0);
                            commandList.remove(0);
                        }
                    }  catch (Exception e) {
                        commandList.clear();
                        try { Thread.sleep(1); } catch (Exception asdf) { asdf.printStackTrace(); }
                        continue;
                    }


                    try {
                        serialPort.writeBytes(c); // writing as array ensures goes at baud rate?

                    } catch (Exception e) { e.printStackTrace();  }

                }

                try { Thread.sleep(1); } catch (Exception e) { e.printStackTrace(); }

            }
        }
    }

}
