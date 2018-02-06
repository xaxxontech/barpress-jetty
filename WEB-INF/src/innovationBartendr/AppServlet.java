package innovationBartendr;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Date;

public class AppServlet extends HttpServlet {
//    public void init(ServletConfig config) throws ServletException {
//        super.init(config);
//    }

    private ArduinoPort device;
    private String content = null;

    public AppServlet() {
        device = new ArduinoPort(this);
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException { doPost(req,res); }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String action = null;
        try { action = request.getParameter("action");
        } catch (Exception e) {	e.printStackTrace(); }

        String val = null;
        try { val = request.getParameter("val");
        } catch (Exception e) {	e.printStackTrace(); }

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        if (action == null) {
            out.println(content());
            return;
        }

        if (!action.equals("getinfo"))  printlog(action+" "+val);

        switch (action) {
            case "getinfo": out.println(getInfo()); break;

            case "connect": device.connect(); break;

            case "disconnect": device.disconnect(); device = new ArduinoPort(this); break;

            case "zero": device.sendCommand(ArduinoPort.ZERO); break;

            case "forceTarget":
                if (val == null) break;
                int lbs = convertToLbs(val);
                if (lbs>255) lbs= 255;
                byte[] cmd = new byte[]{ArduinoPort.FORCETARGET, (byte) lbs};
                device.sendCommand(cmd);
                break;

            case "stop": device.sendCommand(ArduinoPort.STOP); break;

            case "up": device.sendCommand(ArduinoPort.UP); break;

            case "down": device.sendCommand(ArduinoPort.AUTODOWN); break;

            case "slow": device.sendCommand(ArduinoPort.AUTODOWNSLOW); break;

            case "lowerSetpoint":
                if (val == null) break;
                int c = fahrenheitToCelsius(val);
                if (c>255) c= 255;
                cmd = new byte[]{ArduinoPort.LOWERSETPOINT, (byte) c};
                device.sendCommand(cmd);
                break;

            case "upperSetpoint":
                if (val == null) break;
                c = fahrenheitToCelsius(val);
                if (c>255) c= 255;
                cmd = new byte[]{ArduinoPort.UPPERSETPOINT, (byte) c};
                device.sendCommand(cmd);
                break;

            case "autotempon":
                device.sendCommand(ArduinoPort.AUTOTEMPLOWER);
                try { Thread.sleep(100); } catch (Exception e) { e.printStackTrace(); }
                device.sendCommand(ArduinoPort.AUTOTEMPUPPER);
                break;

            case "autotempoff": device.sendCommand(ArduinoPort.AUTOTEMPOFF); break;

            case "fanon": device.sendCommand(ArduinoPort.FANON); break;

            case "fanoff": device.sendCommand(ArduinoPort.FANOFF); break;

            case "shutdownhnow": shutdownhnow(); break;

        }
    }


    private String getInfo() {
        String response = "";

        if (device.connected)     {
            response += "inrhtml('devicestatus','<b>connected</b>');";
            response += "setviz('disconnectlink','');";
            response += "setviz('connectlink','none');";
        }
        else { // not connected
            response += "inrhtml('devicestatus','disconnected');";
            response += "setviz('connectlink','');";
            response += "setviz('disconnectlink','none');";
//            return response;
        }

        response += "inrhtml('currentForce','"+device.currentForce+"');";
        response += "inrhtml('forceTarget','"+device.forceTarget+"');";
        response += "inrhtml('actuatorPos','"+device.actuatorPos+"');";
        response += "inrhtml('actuatorDirection','"+device.actuatorDirection+"');";

        response += "inrhtml('ambientTemp','"+device.ambientTemp+"');";
        response += "inrhtml('upperTemp','"+device.upperTemp+"');";
        response += "inrhtml('lowerTemp','"+device.lowerTemp+"');";
        response += "inrhtml('upperSetpoint','"+device.upperSetpoint+"');";
        response += "inrhtml('lowerSetpoint','"+device.lowerSetpoint+"');";
        response += "inrhtml('tempControlActive','"+device.tempControlActive+"');";

        response += "inrhtml('fan','"+device.fan+"');";

        return response;
    }

    private String content() {
//        if (content != null) return content; // TODO: enable after testing

        content = "";

        try {
            String line;
            FileInputStream filein = new FileInputStream("webapps/innovationBartendr/content.html");
            BufferedReader reader = new BufferedReader(new InputStreamReader(filein));
            while ((line = reader.readLine()) != null)     content += line+"\n";
            reader.close();
            filein.close();
        } catch (Exception e) { e.printStackTrace(); }

        return content;
    }

    protected static void printlog(String str) {
        System.out.println(new Date().toString()+" "+str);
    }

    private int fahrenheitToCelsius(String temp) {
        double c = (Double.valueOf(temp) -32) / 1.8;
        return (int) Math.round(c);
    }

    private int convertToLbs(String psi) {
        double lbs = Double.valueOf(psi)*ArduinoPort.BARSQIN;
        return (int) Math.round(lbs);
    }

    private void shutdownhnow() {
        try { Runtime.getRuntime().exec("/usr/bin/sudo /sbin/shutdown -h now");
        } catch (Exception e) { e.printStackTrace(); }
    }
}
