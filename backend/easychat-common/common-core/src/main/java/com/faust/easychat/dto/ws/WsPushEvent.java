package com.faust.easychat.dto.ws;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WsPushEvent {

    private String toUserId;

    private String type;

    /** JSON 字符串，消费端再反序列化为 payload 对象 */
    private String payloadJson;

    /** 发出这次推送的请求号，方便和网关日志对上 */
    private String traceId;
}
