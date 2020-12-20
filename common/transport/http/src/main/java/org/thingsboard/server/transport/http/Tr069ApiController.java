package org.thingsboard.server.transport.http;
import com.google.api.client.json.Json;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import okhttp3.*;
import org.apache.kafka.common.protocol.types.Field;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.async.DeferredResult;
import sun.rmi.runtime.Log;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Logger;
@RestController
@ConditionalOnExpression("'${service.type:null}'=='tb-transport' || ('${service.type:null}'=='monolith' && '${transport.api_enabled:true}'=='true' && '${transport.http.enabled}'=='true')")
@RequestMapping("/api/v1")
@Slf4j
public class Tr069ApiController {
    public String getToken(){
        String tokenResponse = "";
        try {
            OkHttpClient client = new OkHttpClient();
            FormBody formBody = new FormBody.Builder()
                    .add("username", "admin")
                    .add("password", "admin")
                    .build();
            Request request = new Request.Builder()
                    .url("http://localhost:3000/login")
                    .post(formBody)
                    .build();
            Response response = client.newCall(request).execute();
            tokenResponse = response.body().string();
            System.out.println(tokenResponse);
        }catch (Exception e){
            e.printStackTrace();
        }
        return tokenResponse;
    }
    private String getTR69DeviceList(){
        String acsResponse = null;
        try{
            String token = getToken();
            token = token.replaceAll("^\"|\"$", "");
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("http://localhost:3000/api/devices")
                    .addHeader("Cookie","genieacs-ui-jwt="+token)
                    .build();
            System.out.println(request.headers());
            Response  response = client.newCall(request).execute();
            acsResponse = response.body().string();
        }catch (Exception e){
        }
        return acsResponse;
    }
    private String editTasks(String taskRequest, String deviceID){
        String taskResponse = "";
        try{
            String token = getToken();
            token = token.replaceAll("^\"|\"$", "");
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            okhttp3.RequestBody formBody =  okhttp3.RequestBody.create(JSON,taskRequest);
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("http://localhost:3000/api/devices/"+deviceID+"/tasks")
                    .addHeader("Cookie","genieacs-ui-jwt="+token)
                    .post(formBody)
                    .build();
            Response  response = client.newCall(request).execute();
            taskResponse = response.body().string();
            System.out.println(taskResponse);
        }catch (Exception e){
        }
        return taskResponse;
    }
    @RequestMapping(value = "/tr69/devices", method = RequestMethod.GET,produces = "application/json")
    public DeferredResult<ResponseEntity<?>> getTr069Devices() {
        System.out.println("Received async-deferredresult request");
        DeferredResult<ResponseEntity<?>> output = new DeferredResult<>();
        ForkJoinPool.commonPool().submit(() -> {
            System.out.println("Processing in separate thread");
            String res =  getTR69DeviceList();
            output.setResult(new ResponseEntity<String>(
                    res,
                    HttpStatus.OK));
        });
        return output;
    }
    @RequestMapping(value = "/tr69/tasks", method = RequestMethod.POST,produces = "application/json")
    public DeferredResult<ResponseEntity<?>> editDeviceValues(@RequestBody String deviceTask,@RequestParam(value = "deviceID", required = true, defaultValue = "") String deviceID) {
        System.out.println("Received async-deferredresult request");
        DeferredResult<ResponseEntity<?>> output = new DeferredResult<>();
        ForkJoinPool.commonPool().submit(() -> {
            System.out.println("Processing in separate thread");
            String ResString = editTasks(deviceTask,deviceID);
            output.setResult(new ResponseEntity<>(
                    ResString,
                    HttpStatus.OK));
        });
        return output;
    }
}















