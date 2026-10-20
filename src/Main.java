import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Main {

    public static void main(String[] args)
            throws IOException, InterruptedException {

        HttpClient client =
                HttpClient.newHttpClient();

        String formData =
                "studentId=API001"
                        + "&roomNumber=B-205"
                        + "&name=API Test Student"
                        + "&username=apitest"
                        + "&password=1234";

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(
                                "http://localhost:8080/api/register"
                        ))
                        .header(
                                "Content-Type",
                                "application/x-www-form-urlencoded"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(formData)
                        )
                        .build();

        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        System.out.println(
                "Status: " + response.statusCode()
        );

        System.out.println(
                "Response: " + response.body()
        );
    }
}