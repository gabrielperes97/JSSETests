package gabrielleopoldino.jsse.SSLNonBlocking;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class EchoWorker extends Thread {

    private SocketChannel channel;

    private SSLEngine sslEngine;
    private ByteBuffer outAppData;
    private ByteBuffer outNetData;
    private ByteBuffer inAppData;
    private ByteBuffer inNetData;
    private SSLSession sslSession;

    public EchoWorker(SocketChannel channel) throws IOException {
        this.channel = channel;
        this.channel.configureBlocking(false);

        this.sslEngine = EchoServer.getContext().createSSLEngine();
        this.sslEngine.setUseClientMode(false);
        this.sslEngine.setNeedClientAuth(false);

        this.sslSession = this.sslEngine.getSession();
        this.outAppData = ByteBuffer.allocate(sslSession.getApplicationBufferSize());
        this.inAppData = ByteBuffer.allocate(sslSession.getApplicationBufferSize());
        this.outNetData = ByteBuffer.allocate(sslSession.getPacketBufferSize());
        this.inNetData = ByteBuffer.allocate(sslSession.getPacketBufferSize());

        EchoServer.doHandshake(sslEngine, channel, inAppData, inNetData, outAppData, outNetData);
    }

    @Override
    public void run() {
        while (true)
        {
            try {
                byte msg[];
                String str;
                if ((msg = EchoServer.read(inAppData, inNetData, sslEngine, channel)).length > 0)
                {


                    str = new String(msg, StandardCharsets.UTF_8);
                    System.out.println("Echo: "+str);

                    this.outAppData.clear();
                    this.outAppData.put(msg);
                    EchoServer.send (outAppData, outNetData, sslEngine, channel);
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
                this.interrupt();
            }
        }
    }
}