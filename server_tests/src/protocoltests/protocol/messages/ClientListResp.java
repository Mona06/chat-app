package protocoltests.protocol.messages;

import java.util.List;

public record ClientListResp (String status, Integer code, List<String> users) {

}
