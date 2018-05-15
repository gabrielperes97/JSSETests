package gabrielleopoldino.jsse.TCPNonBlocking;


import java.nio.channels.SocketChannel;

class ServerDataEvent {
    public EchoServer server;
    public SocketChannel socket;
    public byte[] data;

    public ServerDataEvent(EchoServer server, SocketChannel socket, byte[] data) {
        this.server = server;
        this.socket = socket;
        this.data = data;
    }
}