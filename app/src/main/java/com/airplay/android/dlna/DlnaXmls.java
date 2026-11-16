package com.airplay.android.dlna;

/**
 * The static UPnP XML documents: device description and the service control descriptions.
 * They only depend on values substituted through {@code %s} placeholders at build time.
 */
final class DlnaXmls {

    private DlnaXmls() {
    }

    /** SERVER header value, product token per the UPnP spec's shape. */
    static final String SERVER = "Android/13 UPnP/1.0 AirPlayReceiver/1.2";

    private static final String AVTRANSPORT_ACTIONS = ""
            + "<action><name>SetAVTransportURI</name><argumentList>"
            + "<argument name=\"InstanceID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_InstanceID\"/>"
            + "<argument name=\"CurrentURI\" direction=\"in\" relatedStateVariable=\"AVTransportURI\"/>"
            + "<argument name=\"CurrentURIMetaData\" direction=\"in\" relatedStateVariable=\"AVTransportURIMetaData\"/>"
            + "</argumentList></action>"
            + "<action><name>Play</name><argumentList>"
            + "<argument name=\"InstanceID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_InstanceID\"/>"
            + "<argument name=\"Speed\" direction=\"in\" relatedStateVariable=\"TransportPlaySpeed\"/>"
            + "</argumentList></action>"
            + "<action><name>Pause</name><argumentList>"
            + "<argument name=\"InstanceID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_InstanceID\"/>"
            + "</argumentList></action>"
            + "<action><name>Stop</name><argumentList>"
            + "<argument name=\"InstanceID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_InstanceID\"/>"
            + "</argumentList></action>"
            + "<action><name>Seek</name><argumentList>"
            + "<argument name=\"InstanceID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_InstanceID\"/>"
            + "<argument name=\"Unit\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_SeekUnit\"/>"
            + "<argument name=\"Target\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_SeekTarget\"/>"
            + "</argumentList></action>"
            + "<action><name>GetPositionInfo</name><argumentList>"
            + "<argument name=\"InstanceID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_InstanceID\"/>"
            + "<argument name=\"Track\" direction=\"out\" relatedStateVariable=\"NumberOfTracks\"/>"
            + "<argument name=\"TrackDuration\" direction=\"out\" relatedStateVariable=\"CurrentTrackDuration\"/>"
            + "<argument name=\"TrackMetaData\" direction=\"out\" relatedStateVariable=\"CurrentTrackMetaData\"/>"
            + "<argument name=\"TrackURI\" direction=\"out\" relatedStateVariable=\"CurrentTrackURI\"/>"
            + "<argument name=\"RelTime\" direction=\"out\" relatedStateVariable=\"RelativeTimePosition\"/>"
            + "<argument name=\"AbsTime\" direction=\"out\" relatedStateVariable=\"AbsoluteTimePosition\"/>"
            + "</argumentList></action>"
            + "<action><name>GetTransportInfo</name><argumentList>"
            + "<argument name=\"InstanceID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_InstanceID\"/>"
            + "<argument name=\"CurrentTransportState\" direction=\"out\" relatedStateVariable=\"TransportState\"/>"
            + "<argument name=\"CurrentTransportStatus\" direction=\"out\" relatedStateVariable=\"TransportStatus\"/>"
            + "<argument name=\"CurrentSpeed\" direction=\"out\" relatedStateVariable=\"TransportPlaySpeed\"/>"
            + "</argumentList></action>"
            + "<action><name>GetMediaInfo</name><argumentList>"
            + "<argument name=\"InstanceID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_InstanceID\"/>"
            + "<argument name=\"NrTracks\" direction=\"out\" relatedStateVariable=\"NumberOfTracks\"/>"
            + "<argument name=\"MediaDuration\" direction=\"out\" relatedStateVariable=\"CurrentMediaDuration\"/>"
            + "<argument name=\"CurrentURI\" direction=\"out\" relatedStateVariable=\"AVTransportURI\"/>"
            + "<argument name=\"URIMetaData\" direction=\"out\" relatedStateVariable=\"AVTransportURIMetaData\"/>"
            + "<argument name=\"NextURI\" direction=\"out\" relatedStateVariable=\"NextAVTransportURI\"/>"
            + "<argument name=\"NextURIMetaData\" direction=\"out\" relatedStateVariable=\"NextAVTransportURIMetaData\"/>"
            + "<argument name=\"PlayMedium\" direction=\"out\" relatedStateVariable=\"PlaybackStorageMedium\"/>"
            + "<argument name=\"WriteStatus\" direction=\"out\" relatedStateVariable=\"RecordStorageMedium\"/>"
            + "</argumentList></action>"
            + "<action><name>GetTransportSettings</name><argumentList>"
            + "<argument name=\"InstanceID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_InstanceID\"/>"
            + "<argument name=\"PlayMode\" direction=\"out\" relatedStateVariable=\"CurrentPlayMode\"/>"
            + "<argument name=\"RecQualityMode\" direction=\"out\" relatedStateVariable=\"CurrentRecordQualityMode\"/>"
            + "</argumentList></action>"
            + "<action><name>GetDeviceCapabilities</name><argumentList>"
            + "<argument name=\"InstanceID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_InstanceID\"/>"
            + "<argument name=\"PlayMedia\" direction=\"out\" relatedStateVariable=\"PossiblePlaybackStorageMedia\"/>"
            + "<argument name=\"RecMedia\" direction=\"out\" relatedStateVariable=\"PossibleRecordStorageMedia\"/>"
            + "<argument name=\"RecFormats\" direction=\"out\" relatedStateVariable=\"PossibleRecordQualityModes\"/>"
            + "</argumentList></action>";

    private static final String RENDERING_CONTROL_ACTIONS = ""
            + "<action><name>GetVolume</name><argumentList>"
            + "<argument name=\"InstanceID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_InstanceID\"/>"
            + "<argument name=\"Channel\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_Channel\"/>"
            + "<argument name=\"CurrentVolume\" direction=\"out\" relatedStateVariable=\"Volume\"/>"
            + "</argumentList></action>"
            + "<action><name>SetVolume</name><argumentList>"
            + "<argument name=\"InstanceID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_InstanceID\"/>"
            + "<argument name=\"Channel\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_Channel\"/>"
            + "<argument name=\"DesiredVolume\" direction=\"in\" relatedStateVariable=\"Volume\"/>"
            + "</argumentList></action>"
            + "<action><name>GetMute</name><argumentList>"
            + "<argument name=\"InstanceID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_InstanceID\"/>"
            + "<argument name=\"Channel\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_Channel\"/>"
            + "<argument name=\"CurrentMute\" direction=\"out\" relatedStateVariable=\"Mute\"/>"
            + "</argumentList></action>";

    private static final String CONNECTION_MANAGER_ACTIONS = ""
            + "<action><name>GetProtocolInfo</name><argumentList>"
            + "<argument name=\"Source\" direction=\"out\" relatedStateVariable=\"SourceProtocolInfo\"/>"
            + "<argument name=\"Sink\" direction=\"out\" relatedStateVariable=\"SinkProtocolInfo\"/>"
            + "</argumentList></action>"
            + "<action><name>GetCurrentConnectionIDs</name><argumentList>"
            + "<argument name=\"ConnectionIDs\" direction=\"out\" relatedStateVariable=\"CurrentConnectionIDs\"/>"
            + "</argumentList></action>"
            + "<action><name>GetCurrentConnectionInfo</name><argumentList>"
            + "<argument name=\"ConnectionID\" direction=\"in\" relatedStateVariable=\"A_ARG_TYPE_ConnectionID\"/>"
            + "<argument name=\"RcsID\" direction=\"out\" relatedStateVariable=\"A_ARG_TYPE_RcsID\"/>"
            + "<argument name=\"AVTransportID\" direction=\"out\" relatedStateVariable=\"A_ARG_TYPE_AVTransportID\"/>"
            + "<argument name=\"ProtocolInfo\" direction=\"out\" relatedStateVariable=\"A_ARG_TYPE_ProtocolInfo\"/>"
            + "<argument name=\"PeerConnectionManager\" direction=\"out\" relatedStateVariable=\"A_ARG_TYPE_ConnectionManager\"/>"
            + "<argument name=\"PeerConnectionID\" direction=\"out\" relatedStateVariable=\"A_ARG_TYPE_ConnectionID\"/>"
            + "<argument name=\"Direction\" direction=\"out\" relatedStateVariable=\"A_ARG_TYPE_Direction\"/>"
            + "<argument name=\"Status\" direction=\"out\" relatedStateVariable=\"A_ARG_TYPE_ConnectionStatus\"/>"
            + "</argumentList></action>";

    private static final String COMMON_STATES = ""
            + "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_InstanceID</name>"
            + "<dataType>ui4</dataType><defaultValue>0</defaultValue></stateVariable>";

    static String deviceDescription(String friendlyName, String uuid, String urlBase) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<root xmlns=\"urn:schemas-upnp-org:device-1-0\">\n"
                + "<specVersion><major>1</major><minor>0</minor></specVersion>\n"
                + "<URLBase>" + urlBase + "</URLBase>\n"
                + "<device>\n"
                + "<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>\n"
                + "<friendlyName>" + xmlEscape(friendlyName) + "</friendlyName>\n"
                + "<manufacturer>alexqem</manufacturer>\n"
                + "<manufacturerURL>https://github.com/alexqem/airplay-tv-receiver</manufacturerURL>\n"
                + "<modelDescription>AirPlay &amp; DLNA receiver for Android TV</modelDescription>\n"
                + "<modelName>AirPlay Receiver</modelName>\n"
                + "<modelNumber>1.2</modelNumber>\n"
                + "<UDN>uuid:" + uuid + "</UDN>\n"
                + "<serviceList>\n"
                + service("AVTransport", "AVTransport")
                + service("RenderingControl", "RenderingControl")
                + service("ConnectionManager", "ConnectionManager")
                + "</serviceList>\n"
                + "</device>\n"
                + "</root>\n";
    }

    private static String service(String name, String id) {
        return "<service>\n"
                + "<serviceType>urn:schemas-upnp-org:service:" + name + ":1</serviceType>\n"
                + "<serviceId>urn:upnp-org:serviceId:" + id + "</serviceId>\n"
                + "<SCPDURL>/scpd/" + name + ".xml</SCPDURL>\n"
                + "<controlURL>/control/" + name + "</controlURL>\n"
                + "<eventSubURL>/event/" + name + "</eventSubURL>\n"
                + "</service>\n";
    }

    static String avTransportScpd() {
        return scpd("AVTransport", AVTRANSPORT_ACTIONS, COMMON_STATES
                + "<stateVariable sendEvents=\"yes\"><name>TransportState</name>"
                + "<dataType>string</dataType>"
                + "<allowedValueList><allowedValue>STOPPED</allowedValue><allowedValue>PLAYING</allowedValue>"
                + "<allowedValue>PAUSED_PLAYBACK</allowedValue><allowedValue>TRANSITIONING</allowedValue>"
                + "</allowedValueList></stateVariable>"
                + "<stateVariable sendEvents=\"yes\"><name>AVTransportURI</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>CurrentTrackDuration</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>CurrentTrackURI</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>CurrentTrackMetaData</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_SeekUnit</name><dataType>string</dataType>"
                + "<allowedValueList><allowedValue>REL_TIME</allowedValue><allowedValue>ABS_TIME</allowedValue>"
                + "</allowedValueList></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_SeekTarget</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>NumberOfTracks</name><dataType>ui4</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>TransportPlaySpeed</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>RelativeTimePosition</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>AbsoluteTimePosition</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>CurrentMediaDuration</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>NextAVTransportURI</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>NextAVTransportURIMetaData</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>PlaybackStorageMedium</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>RecordStorageMedium</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>CurrentPlayMode</name><dataType>string</dataType><defaultValue>NORMAL</defaultValue></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>CurrentRecordQualityMode</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>AVTransportURIMetaData</name><dataType>string</dataType></stateVariable>");
    }

    static String renderingControlScpd() {
        return scpd("RenderingControl", RENDERING_CONTROL_ACTIONS, COMMON_STATES
                + "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_Channel</name><dataType>string</dataType>"
                + "<allowedValueList><allowedValue>Master</allowedValue></allowedValueList></stateVariable>"
                + "<stateVariable sendEvents=\"yes\"><name>Volume</name><dataType>ui2</dataType>"
                + "<allowedValueRange><minimum>0</minimum><maximum>100</maximum><step>1</step></allowedValueRange></stateVariable>"
                + "<stateVariable sendEvents=\"yes\"><name>Mute</name><dataType>boolean</dataType></stateVariable>");
    }

    static String connectionManagerScpd() {
        return scpd("ConnectionManager", CONNECTION_MANAGER_ACTIONS, ""
                + "<stateVariable sendEvents=\"yes\"><name>SourceProtocolInfo</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"yes\"><name>SinkProtocolInfo</name><dataType>string</dataType>"
                + "<defaultValue>*:*:*,http-get:*:video/*:*,http-get:*:audio/*:*,http-get:*:image/*:*</defaultValue></stateVariable>"
                + "<stateVariable sendEvents=\"yes\"><name>CurrentConnectionIDs</name><dataType>string</dataType>"
                + "<defaultValue>0</defaultValue></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ConnectionID</name><dataType>i4</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_RcsID</name><dataType>i4</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_AVTransportID</name><dataType>i4</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ProtocolInfo</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ConnectionManager</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_Direction</name><dataType>string</dataType></stateVariable>"
                + "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ConnectionStatus</name><dataType>string</dataType></stateVariable>");
    }

    private static String scpd(String service, String actions, String stateVariables) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\n"
                + "<specVersion><major>1</major><minor>0</minor></specVersion>\n"
                + "<actionList>\n" + actions + "\n</actionList>\n"
                + "<serviceStateTable>\n" + stateVariables + "\n</serviceStateTable>\n"
                + "</scpd>\n";
    }

    static String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
