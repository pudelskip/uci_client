import com.sun.org.apache.xalan.internal.xsltc.dom.AdaptiveResultTreeImpl;
import javafx.util.Pair;

import okhttp3.*;
import okio.ByteString;
import org.json.JSONArray;
import org.json.JSONObject;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class Client extends WebSocketListener {

    private final String UCI_LOGIN = "/user/login";
    private final String UCI_WS = "/ws_engine";
    private final String UCI_START_ENGINE = "/engine/start";
    private final String UCI_LIST_ENGINES = "/engine/available";


    public WebSocket webSocket;

    private String endpointUrl;
    private String login;
    private String password;
    private String engine;
    private List<Pair<String,String>> engineOptions;


    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .readTimeout(0,  TimeUnit.MILLISECONDS)
            .build();

    private String authToken;

    public Client() {

        this.engineOptions = new ArrayList<>();
    }

    private void connectToWebsocket() {

        Request request = new Request.Builder()
                .url(this.endpointUrl+UCI_WS)
                .addHeader("Authorization", "Bearer " + this.authToken)
                .build();
        this.webSocket =  httpClient.newWebSocket(request, this);

        // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
        httpClient.dispatcher().executorService().shutdown();
    }

    private static String readFile(String filename) {
        String result = "";
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }
            result = sb.toString();
        } catch(Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    private void loadConfig(){

        String configJsonString = readFile(this.getClass().getResource("config.json").getFile());
        JSONObject configObj = new JSONObject(configJsonString);
        JSONObject engineObj = configObj.getJSONObject("engine");
        JSONObject optionsObj = engineObj.getJSONObject("options");

        this.login = configObj.getString("login");
        this.password = configObj.getString("password");
        this.endpointUrl = configObj.getString("url");
        this.engine = engineObj.getString("name");

        Iterator<String> optionsKeys = optionsObj.keys();

        while(optionsKeys.hasNext()) {
            String key = optionsKeys.next();
            this.engineOptions.add(new Pair(key,optionsObj.getString(key)));

        }

    }


    @Override public void onOpen(WebSocket webSocket, Response response) {
        this.setEngineOptions();
        String test = "ucinewgame\n" +
                "position fen r4rk1/pp5p/2p2ppB/3pP3/2P2Q2/P1N2P2/1q4PP/n4R1K w - - 0 21\n" +
                "go depth 1";

        webSocket.send(test);
//        webSocket.send("Hello...");
//        webSocket.send("...World!");
//        webSocket.send(ByteString.decodeHex("deadbeef"));
//        webSocket.close(1000, "Goodbye, World!");
    }

    @Override public void onMessage(WebSocket webSocket, String text) {
        System.out.println("MESSAGE: " + text);
    }

    @Override public void onMessage(WebSocket webSocket, ByteString bytes) {
        System.out.println("MESSAGE: " + bytes.hex());
    }

    @Override public void onClosing(WebSocket webSocket, int code, String reason) {
        webSocket.close(1000, null);
        System.out.println("CLOSE: " + code + " " + reason);
    }

    @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        t.printStackTrace();
    }

    private  void login() throws IOException {



        String json = new StringBuilder()
                .append("{")
                .append("\"login\":\""+this.login+"\",")
                .append("\"password\":\""+this.password+"\"")
                .append("}").toString();

        // json request body
        RequestBody body = RequestBody.create(
                json,
                MediaType.parse("application/json; charset=utf-8")
        );
        List<Pair<String,String>> headers= new ArrayList<Pair<String,String>>();
        headers.add(new Pair("Content-Type","application/json"));
        String response = this.sendPOST(body, headers, UCI_LOGIN);

        JSONObject responseObj = new JSONObject(response);
        String token = responseObj.getString("token");
        this.authToken = token;
        System.out.println("Logged in with token:"+token);

    }

    private  void startEngine() throws IOException {
        String json = new StringBuilder()
                .append("{")
                .append("\"engine\":\""+this.engine+"\"")
                .append("}").toString();
        System.out.println(json);
        // json request body
        RequestBody body = RequestBody.create(
                json,
                MediaType.parse("application/json; charset=utf-8")
        );
        List<Pair<String,String>> headers= new ArrayList<Pair<String,String>>();
        headers.add(new Pair("Authorization","Bearer " + this.authToken));
//        headers.add(new Pair("Content-Type","application/json"));
//        headers.add(new Pair("Content-Length","28"));
        //String response = client.sendGET(headers,UCI_LIST_ENGINES);
        String response = this.sendPOST(body, headers,UCI_START_ENGINE);

       // System.out.println(response);
        System.out.println(response);

    }

    private void setEngineOptions(){

        for (Pair<String,String> option : this.engineOptions) {
            String command = String.format("setoption name %s value %s", option.getKey(),option.getValue());
            webSocket.send(command);
        }


    }



    private String sendPOST(RequestBody body, List<Pair<String,String>> headers, String path) throws IOException {
        Request.Builder request = new Request.Builder()
                .url(this.endpointUrl+path)
                .post(body);

        for (Pair<String,String> header : headers) {
            request.addHeader(header.getKey(),header.getValue());
        }


        try (Response response = httpClient.newCall(request.build()).execute()) {

            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            return response.body().string();

        }

    }

    private String sendGET(List<Pair<String,String>> headers, String path) throws IOException {
        Request.Builder request = new Request.Builder()
                .url(this.endpointUrl+path)
                .get();

        for (Pair<String,String> header : headers) {
            request.addHeader(header.getKey(),header.getValue());
        }


        try (Response response = httpClient.newCall(request.build()).execute()) {

            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            return response.body().string();

        }

    }

    public static void main(String... args)
    {



        Client client = new Client();
        client.loadConfig();


        try {
            client.login();
            client.startEngine();
            client.connectToWebsocket();
            client.setEngineOptions();
        } catch (IOException e) {
            e.printStackTrace();
        }

//        client.webSocket.send("uci");

    }



}
