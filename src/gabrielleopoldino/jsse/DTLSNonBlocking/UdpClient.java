package gabrielleopoldino.jsse.DTLSNonBlocking;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class UdpClient {

    private DatagramChannel channel;
    private Buffers buffers;
    private SSLEngine sslEngine;
    private SSLSession sslSession;
    private SSLManager sslManager;

    public UdpClient() throws IOException {
        this.channel = DatagramChannel.open();
        this.channel.connect(new InetSocketAddress(UdpCommon.HOST, UdpCommon.PORT));
        this.channel.configureBlocking(false);

        this.sslEngine = UdpCommon.getContext().createSSLEngine("localhost", UdpCommon.PORT);
        this.sslEngine.setUseClientMode(true);
        this.sslEngine.setNeedClientAuth(false);

        this.sslSession = this.sslEngine.getSession();
        this.buffers = new Buffers(this.sslSession.getApplicationBufferSize(), this.sslSession.getPacketBufferSize());

        this.sslManager = new SSLManager(new SSLManager.IO() {
            @Override
            public int write(ByteBuffer data) {
                try {
                    int i = 0;
                    data.flip();
                    while (buffers.outNetData.hasRemaining()) {
                        int j;
                        if ((j=channel.write(data)) < 0)
                            UdpCommon.whenSocketClosed();
                        i += j;
                    }
                    return i;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return -1;
            }

            @Override
            public int read(ByteBuffer data) {
                try {
                    data.clear();
                    int i = channel.read(data);
                    data.flip();
                    return i;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return -1;
            }
        }, sslEngine, buffers);
        this.sslManager.doHandshake();
    }

    public void send(byte[] data) throws IOException {
        sslManager.send(data);

    }

    public byte[] receive() throws IOException {
        return sslManager.receive();
    }

    public static void main (String args[])
    {
        try{
            UdpClient client = new UdpClient();
            System.out.println("Connected");

            Scanner s = new Scanner(System.in);

            Runnable recv = new Runnable() {
                @Override
                public void run() {
                    byte data[];

                    while (true) {
                        try {
                            if ((data = client.receive()).length > 0) {
                                String msg = new String(data, StandardCharsets.UTF_8);
                                System.out.println("Received: " + msg);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                }
            };
            new Thread(recv).start();
            System.out.println("Tudo pronto");
            while (true)
            {

                String msg = s.nextLine();
                client.send(msg.getBytes(StandardCharsets.UTF_8));
                System.out.println("Sending: "+ msg);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
