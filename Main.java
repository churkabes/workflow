public class Main {
    public static void main(String[] args) throws Exception {
        MainWorker cMainWorker = new MainWorker();
        cMainWorker.setHandler(new TestHandler());
        cMainWorker.start();
        //cMainWorker.stop();
    }
}

class TestHandler implements IHandler {
    @Override
    public void onEvent(String name, String data) {
        System.out.println("onEvent -> " +  name + ":" + data);
    }
}

