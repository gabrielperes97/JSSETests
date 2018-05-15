package gabrielleopoldino.jsse.TCPNonBlocking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;

public class Client implements Runnable {

    private Selector selector;
    private ByteBuffer readBuffer = ByteBuffer.allocate(65535);
    private List pendingChanges = new LinkedList();
    private Map pendingData = new HashMap();
    private Map rspHandlers = Collections.synchronizedMap(new HashMap());

    public Client() throws IOException {
        this.selector = initSelector();
    }

    private Selector initSelector() throws IOException {
        return SelectorProvider.provider().openSelector();
    }

    private SocketChannel initiateConnection() throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        socketChannel.connect(new InetSocketAddress("localhost", EchoServer.PORT));

        synchronized (pendingData)
        {
            pendingChanges.add(new ChangeRequest(socketChannel, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
        }

        return socketChannel;
    }

    public void send(byte[] data, RspHandler handler) throws IOException {
        SocketChannel socket = this.initiateConnection();
        rspHandlers.put(socket, handler);

        synchronized (pendingData){
            List queue = (List) pendingData.get(socket);
            if (queue == null)
            {
                queue = new ArrayList();
                pendingData.put(socket, queue);
            }
            queue.add(ByteBuffer.wrap(data));
        }
        selector.wakeup();
    }

    private void handleResponse(SocketChannel socketChannel, byte[] data, int numRead) throws IOException {
        byte[] rspData = new byte[numRead];
        System.arraycopy(data, 0, rspData, 0, numRead);

        RspHandler handler = (RspHandler) this.rspHandlers.get(socketChannel);

        if (handler.handleResponse(rspData)){
            socketChannel.close();
            socketChannel.keyFor(selector).cancel();
        }
    }


    @Override
    public void run() {
        while (true)
        {
            try {
                synchronized (pendingChanges) {
                    Iterator changes = this.pendingChanges.iterator();
                    while (changes.hasNext())
                    {
                        ChangeRequest change = (ChangeRequest) changes.next();
                        switch (change.type)
                        {
                            case ChangeRequest.CHANGEOPS:
                                SelectionKey key = change.socket.keyFor(selector);
                                key.interestOps(change.ops);
                                break;
                            case ChangeRequest.REGISTER:
                                change.socket.register(selector, change.ops);
                                break;
                        }
                    }
                    this.pendingChanges.clear();
                }
                this.selector.select();

                Iterator selectedKeys = this.selector.selectedKeys().iterator();
                while (selectedKeys.hasNext())
                {
                    SelectionKey key = (SelectionKey) selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid())
                    {
                        continue;
                    }

                    if (key.isConnectable())
                    {
                        this.finishConnection(key);
                    } else if (key.isReadable())
                    {
                        this.read(key);
                    } else  if (key.isWritable()){
                        write(key);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void finishConnection(SelectionKey key){
        SocketChannel socketChannel = (SocketChannel) key.channel();

        try{
            socketChannel.finishConnect();
        } catch (IOException e) {
            key.cancel();
            return;
        }

        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();

        this.readBuffer.clear();

        int numRead;
        try
        {
            numRead = channel.read(readBuffer);
        } catch (IOException e) {
            key.cancel();
            channel.close();
            e.printStackTrace();
            return;
        }

        if (numRead == -1)
        {
            key.channel().close();
            key.cancel();
            return;
        }

        this.handleResponse(channel, readBuffer.array(), numRead);
    }

    private void write(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        synchronized (pendingData)
        {
            List queue = (List) pendingData.get(socketChannel);

            while (!queue.isEmpty())
            {
                ByteBuffer buf = (ByteBuffer) queue.get(0);
                socketChannel.write(buf);
                if (buf.remaining() > 0){
                    break;
                }
                queue.remove(0);
            }

            if (queue.isEmpty())
            {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    public static void main(String args[]) {

        try{
            Client client = new Client();
            Thread t = new Thread(client);
            t.setDaemon(true);
            t.start();
            RspHandler handler = new RspHandler();
            client.send("Hello World".getBytes(), handler);
            handler.waitForResponse();
        } catch (IOException e) {
            e.printStackTrace();
        }
    /*
        try {
            SSLEngine engine = EchoServer.getContext().createSSLEngine("localhost", EchoServer.PORT);
            engine.setUseClientMode(true);

            SSLSession session = engine.getSession();
            ByteBuffer in = ByteBuffer.allocate(session.getApplicationBufferSize()+50);
            ByteBuffer out = ByteBuffer.wrap("Hi server, I'm Client".getBytes());
            ByteBuffer to = ByteBuffer.allocateDirect(session.getPacketBufferSize());

            SSLEngineResult result;

            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress("localhost", EchoServer.PORT));

            while (!socketChannel.finishConnect()){}


            doHandshake(socketChannel, engine, myNetData, peerNetData);

            myAppData.put("hello".getBytes());
            myAppData.flip();

            while (myAppData.hasRemaining()) {
                SSLEngineResult res = engine.wrap(myAppData, myNetData);

                if (res.getStatus() == SSLEngineResult.Status.OK){
                    myAppData.compact();
                    while(myNetData.hasRemaining()){
                        int num = socketChannel.write(myNetData);

                        if (num == -1)
                        {

                        }else if (num == 0)
                        {

                        }
                    }
                }
                // Read SSL/TLS encoded data from peer
                int num = socketChannel.read(peerNetData);

                if (num == -1) {
                    // Handle closed channel
                } else if (num == 0) {

                    // No bytes read; try again ...
                } else {
                    // Process incoming data

                    peerNetData.flip();
                    res = engine.unwrap(peerNetData, peerAppData);


                    if (res.getStatus() == SSLEngineResult.Status.OK) {

                        peerNetData.compact();

                        if (peerAppData.hasRemaining()) {
                            // Use peerAppData
                        }
                    }
                }
            }




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
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }*/
    }
}