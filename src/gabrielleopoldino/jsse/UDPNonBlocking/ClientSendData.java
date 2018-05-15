package gabrielleopoldino.jsse.UDPNonBlocking;

import java.net.SocketAddress;

public class ClientSendData {

    private byte[] data;
    private SocketAddress addr;

    public ClientSendData(SocketAddress addr, byte[] data ) {
        this.data = data;
        this.addr = addr;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public SocketAddress getAddr() {
        return addr;
    }

    public void setAddr(SocketAddress addr) {
        this.addr = addr;
    }
}
