package custom_client;


class Main {

    public static void main(String[] args) {
        try {
            Client nodeClient = new Client("localhost", 8080);
            nodeClient.run();
        } catch (Exception e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
        }
    }
}