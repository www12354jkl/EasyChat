package com.faust.easychat.filter;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.MDC;

@Activate(group = { CommonConstants.PROVIDER, CommonConstants.CONSUMER }, order = -9000)
public class TraceIdDubboFilter implements Filter {

    static final String KEY = TraceIdFilter.MDC_KEY;

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String previous = MDC.get(KEY);
        String fromCaller = invocation.getAttachment(KEY);
        boolean adopted = false;
        if ((previous == null || previous.isBlank()) && fromCaller != null && !fromCaller.isBlank()) {
            MDC.put(KEY, fromCaller);
            adopted = true;
        }
        String current = MDC.get(KEY);
        if (current != null && !current.isBlank() && (fromCaller == null || fromCaller.isBlank())) {
            invocation.setAttachment(KEY, current);
        }
        try {
            return invoker.invoke(invocation);
        } finally {
            if (adopted) {
                MDC.remove(KEY);
            }
        }
    }
}
