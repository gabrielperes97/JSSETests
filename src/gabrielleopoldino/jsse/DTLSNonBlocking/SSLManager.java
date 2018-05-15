package gabrielleopoldino.jsse.DTLSNonBlocking;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

public class SSLManager {

    private Logger	logger = Logger.getLogger(this.getClass().getName());
    private IO io;
    private SSLEngine sslEngine;
    private Buffers buffers;
    private SSLEngineResult engineResult = null;

    public SSLManager(IO io, SSLEngine engine, Buffers buffers) {
        this.io = io;
        this.sslEngine = engine;
        this.buffers = buffers;
    }

    public interface IO
    {
        int write(ByteBuffer data);

        int read(ByteBuffer data);

    }

    //Send message through the SSL
    public void send(byte[] data) throws IOException {
        buffers.outAppData.clear();
        buffers.outAppData.put(data);
        buffers.outAppData.flip();

        buffers.outNetData.clear();

        while(buffers.outAppData.hasRemaining())
        {
            SSLEngineResult res = sslEngine.wrap(buffers.outAppData, buffers.outNetData);

            switch (res.getStatus())
            {
                case OK:
                    io.write(buffers.outNetData);
                    break;
            }
        }

    }

    //Receive message through the SSL
    public byte[] receive() throws IOException {
        buffers.inAppData.clear();
        buffers.inNetData.clear();
        SSLEngineResult res;
        int tam = io.read(buffers.inNetData);
        if (tam > 0)
        {
            res = sslEngine.unwrap(buffers.inNetData, buffers.inAppData);
            buffers.inNetData.compact();
            switch (res.getStatus())
            {
                case OK:
                    byte[] msg = new byte[res.bytesProduced()];
                    System.arraycopy(buffers.inAppData.array(), 0, msg, 0, res.bytesProduced());
                    return msg;
                case CLOSED:
                    UdpCommon.whenSSLClosed();
                    break;
                case BUFFER_OVERFLOW:
                    UdpCommon.whenBufferOverflow(sslEngine, buffers.inAppData);
                    break;
                case BUFFER_UNDERFLOW:
                    UdpCommon.whenBufferUnderflow(sslEngine, buffers.inNetData);
                    break;
            }
        }
        return new byte[0];
    }

    public void doHandshake() throws IOException {
        sslEngine.beginHandshake();
        SSLEngineResult.HandshakeStatus hs = sslEngine.getHandshakeStatus();

        while (hs != SSLEngineResult.HandshakeStatus.FINISHED && hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            System.out.println(hs.name());
            switch (hs) {
                case NEED_UNWRAP:
                    buffers.inNetData.clear();
                case NEED_UNWRAP_AGAIN:
                    if (io.read(buffers.inNetData) < 0) {
                        UdpCommon.whenSocketClosed();
                    }
                    SSLEngineResult res = sslEngine.unwrap(buffers.inNetData, buffers.inAppData);
                    buffers.inNetData.compact();
                    hs = res.getHandshakeStatus();

                    switch (res.getStatus()) {
                        case OK:
                            //q q eu faço???
                            break;
                        case CLOSED:
                            UdpCommon.whenSSLClosed();
                            break;
                        case BUFFER_OVERFLOW:
                            UdpCommon.whenBufferOverflow(sslEngine, buffers.inAppData);
                            break;
                        case BUFFER_UNDERFLOW:
                            UdpCommon.whenBufferUnderflow(sslEngine, buffers.inNetData);
                            break;
                    }
                    break;
                case NEED_WRAP:
                    buffers.outNetData.clear();

                    res = sslEngine.wrap(buffers.outAppData, buffers.outNetData);
                    hs = res.getHandshakeStatus();

                    switch (res.getStatus()) {
                        case OK:
                            io.write(buffers.outNetData);
                            break;
                        case CLOSED:
                            UdpCommon.whenSSLClosed();
                            break;
                        case BUFFER_OVERFLOW:
                            UdpCommon.whenBufferOverflow(sslEngine, buffers.outAppData);
                            break;
                        case BUFFER_UNDERFLOW:
                            UdpCommon.whenBufferUnderflow(sslEngine, buffers.outNetData);
                            break;

                    }
                    break;
                case NEED_TASK:
                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null) {
                        //new Thread(task).start();
                        task.run();
                    }
                    hs = sslEngine.getHandshakeStatus();
                    break;
                default:
                    hs = sslEngine.getHandshakeStatus();
                    break;
            }
        }
    }
}
