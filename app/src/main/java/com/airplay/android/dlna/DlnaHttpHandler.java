package com.airplay.android.dlna;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;

/**
 * The renderer's HTTP face: device/SCPD documents, SOAP control endpoints and the (minimal)
 * GENA event subscription endpoint.
 *
 * <p>Runs on a Netty event loop like the AirPlay server; every request is answered from the
 * current {@link DlnaController} state, so no request ever needs to talk to the player thread.
 */
final class DlnaHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String XML_TYPE = "text/xml; charset=\"utf-8\"";
    /** 1800s default matches the SSDP cache age. */
    private static final String CALLBACK_TIMEOUT = "SECOND-1800";

    private final String friendlyName;
    private final String uuid;
    private final String urlBase;
    private final DlnaController controller;

    DlnaHttpHandler(String friendlyName, String uuid, String urlBase, DlnaController controller) {
        this.friendlyName = friendlyName;
        this.uuid = uuid;
        this.urlBase = urlBase;
        this.controller = controller;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = request.uri();
        String method = request.method().name();

        FullHttpResponse response;
        if (path.startsWith("/description.xml")) {
            response = xmlResponse(DlnaXmls.deviceDescription(friendlyName, uuid, urlBase));
        } else if (path.startsWith("/scpd/AVTransport.xml")) {
            response = xmlResponse(DlnaXmls.avTransportScpd());
        } else if (path.startsWith("/scpd/RenderingControl.xml")) {
            response = xmlResponse(DlnaXmls.renderingControlScpd());
        } else if (path.startsWith("/scpd/ConnectionManager.xml")) {
            response = xmlResponse(DlnaXmls.connectionManagerScpd());
        } else if ("SUBSCRIBE".equals(method) && path.startsWith("/event/")) {
            // Eventing is not implemented: control points that rely on it fall back to polling
            // GetTransportInfo/GetPositionInfo, which every one tried (KMPlayer, BubbleUPnP) does
            response = subscriptionResponse();
        } else if ("POST".equals(method) && path.startsWith("/control/")) {
            response = handleSoap(request);
        } else {
            response = textResponse(HttpResponseStatus.NOT_FOUND, "not found");
        }

        HttpUtil.setContentLength(response, response.content().readableBytes());
        ctx.writeAndFlush(response);
    }

    private FullHttpResponse handleSoap(FullHttpRequest request) {
        String service = request.uri().substring("/control/".length());
        String actionHeader = request.headers().get("SOAPACTION", "");
        String body = request.content().toString(java.nio.charset.StandardCharsets.UTF_8);
        String action = actionHeader.replace("\"", "");
        int hash = action.lastIndexOf('#');
        action = hash >= 0 ? action.substring(hash + 1) : action;
        if (action.isEmpty()) {
            // Some control points omit the header; fall back to the body's first action tag
            action = new SoapActionProbe(body).invoke().actionName();
        }

        try {
            return xmlResponse(handleAction(service, action, body));
        } catch (SoapFault e) {
            return soapFault(e);
        }
    }

    private String handleAction(String service, String action, String body) throws SoapFault {
        switch (service) {
            case "AVTransport":
                return avTransport(action, body);
            case "RenderingControl":
                return renderingControl(action, body);
            case "ConnectionManager":
                return connectionManager(action);
            default:
                throw new SoapFault(404, "Invalid Action", "Unknown service " + service);
        }
    }

    private String avTransport(String action, String body) throws SoapFault {
        switch (action) {
            case "SetAVTransportURI":
                String uri = arg(body, action, "CurrentURI");
                if (uri == null || uri.isEmpty()) {
                    throw new SoapFault(716, "Resource not found", "empty CurrentURI");
                }
                controller.onSetUri(uri, arg(body, action, "CurrentURIMetaData"));
                return response(action, "AVTransport", "");
            case "Play":
                controller.onPlay();
                return response(action, "AVTransport", "");
            case "Pause":
                controller.onPause();
                return response(action, "AVTransport", "");
            case "Stop":
                controller.onStopPlayback();
                return response(action, "AVTransport", "");
            case "Seek": {
                String unit = arg(body, action, "Unit");
                String target = arg(body, action, "Target");
                if (!"REL_TIME".equals(unit) && !"ABS_TIME".equals(unit)) {
                    throw new SoapFault(710, "Transition not available",
                            "only time seeks are supported");
                }
                long ms = DlnaController.parseClock(target);
                if (ms < 0) {
                    throw new SoapFault(710, "Transition not available", "bad Target " + target);
                }
                controller.onSeek(ms);
                return response(action, "AVTransport", "");
            }
            case "GetPositionInfo":
                return response(action, "AVTransport",
                        out("Track", "0")
                                + out("TrackDuration",
                                DlnaController.formatClock(controller.getDurationMs()))
                                + out("TrackMetaData", "")
                                + out("TrackURI", DlnaXmls.xmlEscape(controller.getCurrentUri()))
                                + out("RelTime", DlnaController.formatClock(controller.getPositionMs()))
                                + out("AbsTime", DlnaController.formatClock(controller.getPositionMs())));
            case "GetTransportInfo":
                return response(action, "AVTransport",
                        out("CurrentTransportState", controller.getTransportState())
                                + out("CurrentTransportStatus", "OK")
                                + out("CurrentSpeed", "1"));
            case "GetMediaInfo":
                return response(action, "AVTransport",
                        out("NrTracks", "0")
                                + out("MediaDuration",
                                DlnaController.formatClock(controller.getDurationMs()))
                                + out("CurrentURI", DlnaXmls.xmlEscape(controller.getCurrentUri()))
                                + out("URIMetaData", "")
                                + out("NextURI", "")
                                + out("NextURIMetaData", "")
                                + out("PlayMedium", "NETWORK")
                                + out("WriteStatus", "NOT_WRITABLE"));
            case "GetTransportSettings":
                return response(action, "AVTransport",
                        out("PlayMode", "NORMAL") + out("RecQualityMode", "NOT_IMPLEMENTED"));
            case "GetDeviceCapabilities":
                return response(action, "AVTransport",
                        out("PlayMedia", "NETWORK")
                                + out("RecMedia", "NOT_IMPLEMENTED")
                                + out("RecFormats", "NOT_IMPLEMENTED"));
            default:
                throw new SoapFault(401, "Invalid Action", action + " not supported");
        }
    }

    private String renderingControl(String action, String body) throws SoapFault {
        switch (action) {
            case "GetVolume":
                return response(action, "RenderingControl",
                        out("CurrentVolume", String.valueOf(PlayerState.volume)));
            case "SetVolume":
                String desired = arg(body, action, "DesiredVolume");
                try {
                    PlayerState.volume = Math.max(0, Math.min(100, Integer.parseInt(desired)));
                } catch (NumberFormatException e) {
                    throw new SoapFault(402, "Invalid Args", "DesiredVolume " + desired);
                }
                controller.onVolume(PlayerState.volume);
                return response(action, "RenderingControl", "");
            case "GetMute":
                return response(action, "RenderingControl", out("CurrentMute", "0"));
            default:
                throw new SoapFault(401, "Invalid Action", action + " not supported");
        }
    }

    /** Volume is stored on the renderer side so it survives the player activity restarting. */
    private static final class PlayerState {
        static int volume = 100;
    }

    private String connectionManager(String action) throws SoapFault {
        switch (action) {
            case "GetProtocolInfo":
                return response(action, "ConnectionManager",
                        out("Source", "")
                                + out("Sink", "*:*:*,http-get:*:video/*:*,http-get:*:audio/*:*,"
                                + "http-get:*:image/*:*,http-get:*:text/*:*"));
            case "GetCurrentConnectionIDs":
                return response(action, "ConnectionManager", out("ConnectionIDs", "0"));
            case "GetCurrentConnectionInfo":
                return response(action, "ConnectionManager",
                        out("RcsID", "0")
                                + out("AVTransportID", "0")
                                + out("ProtocolInfo", "")
                                + out("PeerConnectionManager", "")
                                + out("PeerConnectionID", "-1")
                                + out("Direction", "Input")
                                + out("Status", "OK"));
            default:
                throw new SoapFault(401, "Invalid Action", action + " not supported");
        }
    }

    // ---- SOAP document helpers ----------------------------------------------------------

    /** Reads the first direct {@code <Arg>} child of the action element out of the SOAP body. */
    private static String arg(String body, String action, String argument) {
        // The action element is namespace-prefixed (<u:Play ...>), so match the bare name and
        // cut the scope between the end of the opening tag and the closing tag
        int nameAt = body.indexOf(action);
        if (nameAt < 0) return null;
        int contentStart = body.indexOf('>', nameAt);
        if (contentStart < 0) return null;
        int closeAt = body.indexOf(action, contentStart);
        if (closeAt < 0) closeAt = body.length();
        String scope = body.substring(contentStart + 1, closeAt);

        int argStart = scope.indexOf("<" + argument);
        if (argStart < 0) return null;
        int valueStart = scope.indexOf('>', argStart);
        if (valueStart < 0) return null;
        int valueEnd = scope.indexOf("</" + argument + ">", valueStart);
        if (valueEnd < 0) return null;
        return unescape(scope.substring(valueStart + 1, valueEnd).trim());
    }

    private static String unescape(String value) {
        return value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
    }

    private static String out(String name, String value) {
        return "<" + name + ">" + (value == null ? "" : value) + "</" + name + ">";
    }

    private static String response(String action, String service, String outArgs) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n"
                + "<s:Body>\n"
                + "<u:" + action + "Response xmlns:u=\"urn:schemas-upnp-org:service:" + service
                + ":1\">\n" + outArgs + "\n</u:" + action + "Response>\n"
                + "</s:Body>\n</s:Envelope>\n";
    }

    private static FullHttpResponse soapFault(SoapFault fault) {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n"
                + "<s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring>\n"
                + "<detail><UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\">"
                + "<errorCode>" + fault.code + "</errorCode>"
                + "<errorDescription>" + DlnaXmls.xmlEscape(fault.description) + "</errorDescription>"
                + "</UPnPError></detail></s:Fault></s:Body>\n</s:Envelope>\n";
        return xmlResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, xml);
    }

    private static FullHttpResponse xmlResponse(String xml) {
        return xmlResponse(HttpResponseStatus.OK, xml);
    }

    private static FullHttpResponse xmlResponse(HttpResponseStatus status, String xml) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.wrappedBuffer(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, XML_TYPE);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        return response;
    }

    private static FullHttpResponse subscriptionResponse() {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER);
        response.headers().set(HttpHeaderNames.SERVER, DlnaXmls.SERVER);
        response.headers().set("SID", "uuid:" + java.util.UUID.randomUUID());
        response.headers().set("TIMEOUT", CALLBACK_TIMEOUT);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        return response;
    }

    private static FullHttpResponse textResponse(HttpResponseStatus status, String text) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.wrappedBuffer(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        return response;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

    /** Minimal UPnP fault for control endpoints. */
    private static final class SoapFault extends Exception {
        final int code;
        final String description;

        SoapFault(int code, String description, String detail) {
            super(description + (detail == null ? "" : ": " + detail));
            this.code = code;
            this.description = description;
        }
    }

    /** Fallback action-name probe for control points that omit the SOAPACTION header. */
    private static final class SoapActionProbe {
        private final String body;
        private String actionName = "Unknown";

        SoapActionProbe(String body) {
            this.body = body;
        }

        SoapActionProbe invoke() {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("<([A-Za-z0-9]+:)?([A-Za-z0-9_]+)[^>]*xmlns:u=")
                    .matcher(body);
            if (matcher.find()) {
                actionName = matcher.group(2);
            }
            return this;
        }

        String actionName() {
            return actionName;
        }
    }
}
