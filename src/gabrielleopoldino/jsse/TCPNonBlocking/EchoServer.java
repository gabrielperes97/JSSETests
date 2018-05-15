package gabrielleopoldino.jsse.TCPNonBlocking;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;

public class EchoServer implements Runnable {

    public static final int PORT = 6600;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private ByteBuffer readBuffer = ByteBuffer.allocate(65535);
    private List changeRequests = new LinkedList();
    private Map pendingData = new HashMap();

    private EchoWorker worker;

    public EchoServer(EchoWorker worker) throws IOException {
        this.selector = this.initSelector();
        this.worker = worker;
    }

    private Selector initSelector() throws IOException {
        Selector selector = SelectorProvider.provider().openSelector();
        this.serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(PORT));

        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        return selector;
    }

    @Override
    public void run() {
        while (true)
        {
            try {
                synchronized (changeRequests) {
                    Iterator changes = this.changeRequests.iterator();
                    while (changes.hasNext())
                    {
                        ChangeRequest change = (ChangeRequest) changes.next();
                        switch (change.type)
                        {
                            case ChangeRequest.CHANGEOPS:
                                SelectionKey key = change.socket.keyFor(selector);
                                key.interestOps(change.ops);
                        }
                    }
                    this.changeRequests.clear();
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

                    if (key.isAcceptable())
                    {
                        this.accept(key);
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

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        SocketChannel socketChannel = serverSocketChannel.accept();
        Socket socket = socketChannel.socket();
        socketChannel.configureBlocking(false);

        socketChannel.register(selector, SelectionKey.OP_READ);
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

        this.worker.processData(this, channel, readBuffer.array(), numRead);
    }

    public void send(SocketChannel socket, byte[] data)
    {
        synchronized (changeRequests)
        {
            changeRequests.add(new ChangeRequest(socket, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));

            synchronized (pendingData)
            {
                List queue = (List) pendingData.get(socket);
                if (queue == null)
                {
                    queue = new ArrayList();
                    pendingData.put(socket, queue);
                }
                queue.add(ByteBuffer.wrap(data));
            }
        }
        this.selector.wakeup();
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

    public static void main (String args[])
    {
        try {
            EchoWorker worker = new EchoWorker();
            new Thread(worker).start();
            new Thread(new EchoServer(worker)).start();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

}
