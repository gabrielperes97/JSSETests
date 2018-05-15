package gabrielleopoldino.jsse.UDPNonBlocking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class UdpClient {

    private ByteBuffer inData;
    private ByteBuffer outData;
    private DatagramChannel channel;

    public UdpClient() throws IOException {
        this.inData = ByteBuffer.allocate(65535);
        this.outData = ByteBuffer.allocate(65535);
        this.channel = DatagramChannel.open();
        this.channel.connect(new InetSocketAddress(UdpCommon.HOST, UdpCommon.PORT));
        this.channel.configureBlocking(true);
    }

    public void send(byte[] data) throws IOException {
        outData.clear();
        outData.put(data);
        outData.flip();
        this.channel.write(outData);
    }

    public byte[] receive() throws IOException {
        inData.clear();
        int i = this.channel.read(inData);
        inData.flip();
        if (i > 0)
        {
            byte b[] = new byte[i];
            System.arraycopy(inData.array(), 0, b, 0, i);
            return b;
        }
        return new byte[0];
    }

    public static void main (String args[])
    {
        try{
            UdpClient client = new UdpClient();
            System.out.println("Connected");

            Scanner s = new Scanner(System.in);

            while (true)
            {
                String msg = s.nextLine();
                client.send(msg.getBytes(StandardCharsets.UTF_8));
                System.out.println("Sending: "+ msg);

                msg = new String(client.receive(), StandardCharsets.UTF_8);
                System.out.println("Received: "+ msg);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
