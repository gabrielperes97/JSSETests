package gabrielleopoldino.jsse.DTLSNonBlocking;

import java.nio.ByteBuffer;

public class Buffers {

    public ByteBuffer outAppData;
    public ByteBuffer outNetData;
    public ByteBuffer inAppData;
    public ByteBuffer inNetData;

    public Buffers(int applicationBufferSize, int networkBufferSize) {
        this.outAppData = ByteBuffer.allocate(applicationBufferSize);
        this.inAppData = ByteBuffer.allocate(applicationBufferSize);
        this.outNetData = ByteBuffer.allocate(networkBufferSize);
        this.inNetData = ByteBuffer.allocate(networkBufferSize);
    }
}
