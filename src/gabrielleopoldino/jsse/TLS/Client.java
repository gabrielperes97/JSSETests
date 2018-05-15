package gabrielleopoldino.jsse.TLS;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Scanner;

public class Client {

    public static void main (String args[])
    {


        try {
            SSLSocketFactory factory = EchoServer.getContext().getSocketFactory();
            SSLSocket socket = (SSLSocket) factory.createSocket("localhost", EchoServer.PORT);

            System.out.println("Connected to " + socket.getRemoteSocketAddress());

            Scanner s = new Scanner(System.in);
            PrintStream out = new PrintStream(socket.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            while (true) {
                String msg = s.nextLine();
                if (msg.length() != 0) {
                    out.println(msg);
                    out.flush();
                    System.out.println(in.readLine());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
