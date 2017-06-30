package vandyke.siamobile;

import android.app.Activity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;

public class LocalWallet {

    private static LocalWallet instance;

    private String seed;
    private ArrayList<String> addresses;

    private ServerSocket socket;
    private Thread socketThread;

    private File binary;

    private LocalWallet() {
        seed = MainActivity.prefs.getString("localWalletSeed", "noseed");
        addresses = new ArrayList<>(MainActivity.prefs.getStringSet("localWalletAddresses", new HashSet<String>()));
    }

    public static LocalWallet getInstance() {
        if (instance == null)
            instance = new LocalWallet();
        return instance;
    }

    public void startListening(final int port) {
        if (socketThread != null || socketThread.isAlive()) {
            System.out.println("localwallet is already listening");
            return;
        }
        try {
            socket = new ServerSocket(port);
            socketThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        while (true) {
                            System.out.println("waiting for connection");
                            Socket client = socket.accept(); // might want to fork when accepting, in case user sends lots at once
                            System.out.println("something connected to socket");
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            PrintWriter out = new PrintWriter(client.getOutputStream());

                            String line;
                            while ((line = in.readLine()) != null) {
                                System.out.println(line);
                            }

                            if (line == null)
                                continue;

                            // probably need some header stuff first
                            out.print("HTTP/1.1 ");

                            if (line.contains("/wallet/address")) {
                                out.print("200 OK\n\n");
                                JSONObject response = new JSONObject();
                                response.put("address", addresses.get((int)(Math.random() * addresses.size())));
                                out.print(response.toString());
                            } else if (line.contains("/wallet/addresses")) {
                                out.print("200 OK\n\n");
                                JSONObject response = new JSONObject();
                                JSONArray addressArray = new JSONArray();
                                for (String address : addresses)
                                    addressArray.put(address);
                                response.put("address", addressArray);
                                out.print(response.toString());
                            } else {
                                out.print("501 Not Implemented");
                                out.print("{\"message\":\"unsupported on local wallet\"}");
                            }
                            in.close();
                            out.close();
                            client.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
            socketThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void newWallet(Activity activity) {
        try {
            binary = MainActivity.copyBinary("sia-coldstorage", activity);
            ArrayList<String> fullCommand = new ArrayList<>();
            fullCommand.add(0, binary.getAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(fullCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder stdOut = new StringBuilder();
            BufferedReader inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            int read;
            char[] buffer = new char[1024];
            while ((read = inputReader.read(buffer)) > 0) {
                stdOut.append(new String(buffer), 0, read);
            }
            inputReader.close();
            // parse the output
            JSONObject json = new JSONObject(stdOut.toString());
            seed = json.getString("Seed");
            JSONArray addressesJson = json.getJSONArray("Addresses");
            addresses.clear();
            for (int i = 0; i < addressesJson.length(); i++)
                addresses.add(addressesJson.getString(i));
            System.out.println(seed);
            System.out.println(addresses);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
