import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import java.util.HashMap;

interface IHandler {
    void onEvent(String name, String data);
}

public class MainWorker {
    private static WebSocketClient ws;
    private static Boolean bAllowReconnect = true;
    private static String sUrlParameters = "country[]=29&country[]=25&country[]=54&country[]=145&country[]=47&country[]=34&country[]=174&country[]=163&country[]=32&country[]=70&country[]=6&country[]=27&country[]=37&country[]=122&country[]=15&country[]=113&country[]=107&country[]=55&country[]=24&country[]=121&country[]=59&country[]=89&country[]=72&country[]=71&country[]=22&country[]=17&country[]=51&country[]=39&country[]=93&country[]=106&country[]=14&country[]=48&country[]=33&country[]=23&country[]=10&country[]=35&country[]=92&country[]=57&country[]=94&country[]=97&country[]=68&country[]=96&country[]=103&country[]=111&country[]=42&country[]=109&country[]=188&country[]=7&country[]=105&country[]=172&country[]=21&country[]=43&country[]=20&country[]=60&country[]=87&country[]=44&country[]=193&country[]=125&country[]=45&country[]=53&country[]=38&country[]=170&country[]=100&country[]=56&country[]=80&country[]=52&country[]=36&country[]=90&country[]=112&country[]=110&country[]=11&country[]=26&country[]=162&country[]=9&country[]=12&country[]=46&country[]=85&country[]=41&country[]=202&country[]=63&country[]=123&country[]=61&country[]=143&country[]=4&country[]=5&country[]=138&country[]=178&country[]=84&country[]=75&timeZone=8&timeFilter=timeRemain&currentTab=today&submitFilters=1&limit_from=0";
    //TIME WORKER
    private static Thread tWorker;
    HashMap<String, String> eventList = new HashMap<String, String>();
    private IHandler EventHandler;

    public static void stop() {
        bAllowReconnect = false;
        ws.close();
        System.out.println("Stop");
    }

    void setHandler(IHandler handler) {
        EventHandler = handler;
    }

    void start() throws Exception {

        HashMap<String, String> Headers = new HashMap<>();
        Headers.put("Content-Type", "application/x-www-form-urlencoded");
        Headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");
        Headers.put("X-Requested-With", "XMLHttpRequest");

        URLConnectionReader calendarParser = new URLConnectionReader("https://www.investing.com/economic-calendar/Service/getCalendarFilteredData", sUrlParameters, Headers);
        eventList = calendarParser.format();

        ws = new WebSocketClient(URLConnectionReader.getActualUrl(), WebSocketVersion.V08);
        ws.setHandler(new WebSocketClient.WebsocketHandler() {

            @Override
            public void onOpen() {
                System.out.println(eventList);
                ws.send("[\"{\\\"_event\\\":\\\"subscribe\\\",\\\"tzID\\\":55,\\\"message\\\":\\\"pid-8839:\\\"}\"]");
                ws.send("[\"{\\\"_event\\\":\\\"subscribe\\\",\\\"tzID\\\":55,\\\"message\\\":\\\"pid-8874:\\\"}\"]");
                ws.send("[\"{\\\"_event\\\":\\\"subscribe\\\",\\\"tzID\\\":55,\\\"message\\\":\\\"pid-169:\\\"}\"]");
                ws.send("[\"{\\\"_event\\\":\\\"subscribe\\\",\\\"tzID\\\":55,\\\"message\\\":\\\"pid-1:\\\"}\"]");
                ws.send("[\"{\\\"_event\\\":\\\"subscribe\\\",\\\"tzID\\\":55,\\\"message\\\":\\\"pid-956731:\\\"}\"]");
                ws.send("[\"{\\\"_event\\\":\\\"subscribe\\\",\\\"tzID\\\":55,\\\"message\\\":\\\"pid-8827:\\\"}\"]");

                //Subscribe to events
                for (String key : eventList.keySet()) {
                    ws.send("[\"{\\\"_event\\\":\\\"subscribe\\\",\\\"tzID\\\":55,\\\"message\\\":\\\"" + key + ":\\\"}\"]");
                }
                //Support connection
                startBefore(() -> ws.send("[\"{\\\"_event\\\":\\\"heartbeat\\\",\\\"data\\\":\\\"h\\\"}\"]"), 3000, true);
                //Reparse Economic calendar
                startAfter(() -> {
                    try {
                        eventList.putAll(calendarParser.format());

                        for (String key : eventList.keySet()) {
                            ws.send("[\"{\\\"_event\\\":\\\"subscribe\\\",\\\"tzID\\\":55,\\\"message\\\":\\\"" + key + ":\\\"}\"]");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                }, 300000, true);
            }

            @Override
            public void onRead(TextWebSocketFrame frame) {
                if (frame.text().contains("event-")) {
                    String name = frame.text().replaceAll("(message|[^event\\-\\d\\:])", "").split("::")[0].replace(":", "");
                    String key = eventList.get(name);
                    if (key != eventList.get(name)) {
                        EventHandler.onEvent(key, frame.text());
                    }
                }
            }

            @Override
            public void onClose() {
                try {
                    if (bAllowReconnect) {
                        start();
                    }
                } catch (Exception e) {
                }
            }
        });
        ws.connect();
    }

    public void startAfter(Runnable runnable, int delay, boolean loop) {
        tWorker = new Thread(() ->
        {
            try {

                Thread.sleep(delay);
                runnable.run();
                if (loop) {
                    this.startAfter(runnable, delay, true);
                }

            } catch (Exception e) {
                e.fillInStackTrace();
            }
        });
        tWorker.start();
    }

    public void startBefore(Runnable runnable, int delay, boolean loop) {
        tWorker = new Thread(() ->
        {
            try {
                runnable.run();
                Thread.sleep(delay);
                if (loop) {
                    this.startBefore(runnable, delay, true);
                }

            } catch (Exception e) {
                e.fillInStackTrace();
            }
        });
        tWorker.start();
    }
}
