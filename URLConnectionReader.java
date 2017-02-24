import com.google.gson.JsonParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Random;

public class URLConnectionReader {

    private static String sUrlAddress;
    private static String sUrlParameters;
    private static HashMap<String, String> headers;

    URLConnectionReader(String sUrl, String PostData, HashMap<String, String> hmHeaders) throws Exception {
        sUrlAddress = sUrl;
        sUrlParameters = PostData;
        headers = hmHeaders;
    }

    static String getActualUrl() throws Exception {
        String pattern = "abcdefghijklmnopqrstuvwxyz0123456789";
        String serverId = "";
        String serverHash = "";
        String serverEcho = "";
        int patternLength = pattern.length();

        try {
            for (int c = 0; c < 8; c++) {
                int random = (int) Math.floor(Math.random() * patternLength);
                serverHash += pattern.substring(random, random + 1);
            }

            serverEcho = String.valueOf(new Random().nextInt(999 - 101) + 101);

            Elements elements = Jsoup.connect("https://www.investing.com/economic-calendar/").get().getElementsByTag("script");
            for (Element elem : elements) {
                if (elem.html().contains("window.stream")) {
                    serverId = elem.html().replaceAll("(\\D|443)", "");
                }
            }

            return String.format("wss://stream%s.forexpros.com/echo/%s/%s/websocket", serverId, serverEcho, serverHash);
        } catch (Exception ex) {
            serverId = serverId.isEmpty() ? "78" : serverId;
            serverEcho = serverEcho.isEmpty() ? "467" : serverEcho;
            serverHash = serverHash.isEmpty() ? "9hia97l0" : serverHash;
            return String.format("wss://stream%s.forexpros.com/echo/%s/%s/websocket", serverId, serverEcho, serverHash);
        }
    }

    String parse() throws Exception {
        URL obj = new URL(sUrlAddress);
        String urlParameters = sUrlParameters;

        HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

        con.setRequestMethod("POST");
        con.setDoOutput(true);

        for (String key : headers.keySet()) {
            con.setRequestProperty(key, URLConnectionReader.headers.get(key));
        }

        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();


        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }

    HashMap<String, String> format() throws Exception {
        String html = parse();
        html = new JsonParser().parse(html).getAsJsonObject().get("data").toString().replace("\\", "");

        HashMap<String, String> EventList = new HashMap<String, String>();
        Document doc = Jsoup.parse("<table><tbody>" + html + "</tbody></table>");

        for (int i = 1; i < doc.select("tr").size(); i++) {
            String eventId = doc.select("tr").get(i).id().replace("eventRowId_", "event-");
            String eventcurrencyName = doc.select("tr").get(i).select("td.left.flagCur.noWrap").text().replaceAll("\\W", "");
            String eventName = doc.select("tr").get(i).select("td.left.event > a").text().replaceAll("([\\(\\)\\s_-])|(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)", "");
            String eventAcutal = doc.select("tr").get(i).select("td." + eventId + "-actual").text();

            if (!eventAcutal.contains(".") & !eventAcutal.contains("%")) {
                EventList.put(eventId, eventcurrencyName + eventName);
            }
        }
        return EventList;
    }
}
