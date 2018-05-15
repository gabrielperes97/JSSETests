package gabrielleopoldino.jsse.TLS;

import javax.net.ssl.*;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;

public class EchoServer {

    public static final int PORT = 6600;

    public static SSLContext getContext()
    {
        SSLContext context = null;
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream("foobar"), "foobar".toCharArray());

            KeyManagerFactory keyFact = KeyManagerFactory.getInstance("SunX509");
            keyFact.init(keyStore, "foobar".toCharArray());

            TrustManagerFactory trustFact = TrustManagerFactory.getInstance("SunX509");
            trustFact.init(keyStore);

            context = SSLContext.getInstance("TLS");
            context.init(keyFact.getKeyManagers(), trustFact.getTrustManagers(), null);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return context;
    }

    public static void main (String args[])
    {
        try {
            SSLServerSocketFactory factory = getContext().getServerSocketFactory();
            SSLServerSocket serverSocker = (SSLServerSocket) factory.createServerSocket(PORT);
            SSLSocket socket = (SSLSocket) serverSocker.accept();

            System.out.println("Conected to " + socket.getRemoteSocketAddress());

            PrintStream out = new PrintStream(socket.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            while (true) {
                String message = in.readLine();
                if (message != null) {
                    /*break;*/
                    System.out.println(message);
                    out.println(message);
                    out.flush();
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
