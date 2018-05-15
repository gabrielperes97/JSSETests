package gabrielleopoldino.jsse;

import java.security.Provider;
import java.security.Security;
import java.util.Iterator;

public class TestProvider {

    public static void main (String args[])
    {
        String providerName = "BCJSSE";
        Provider provider;

        if ((provider = Security.getProvider(providerName)) == null)
        {
            System.out.println(providerName + " not instaled");
        }
        else
        {
            System.out.println(providerName + " installed");

            Iterator it = provider.keySet().iterator();
            while (it.hasNext())
            {
                String entry = (String)it.next();
                // this indicates the entry actually refers to another entry
                if (entry.startsWith("Alg.Alias."))
                {
                    entry = entry.substring("Alg.Alias.".length());
                }
                String factoryClass = entry.substring(0, entry.indexOf('.'));
                String name = entry.substring(factoryClass.length() + 1);
                System.out.println(factoryClass + ": " + name);
            }
        }
    }
}
