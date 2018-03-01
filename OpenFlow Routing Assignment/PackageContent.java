public interface PackageContent 
{
	/*
	 * Applies to all packets
	 */
	public static final int HEADER_INDEX = 0;
	public static final int HEADER_LENGTH = 1;
	public static final int FOOTER_LENGTH = 1;
	public static final int FOOTER_VALUE = 0;
	
	/*
	 * Data and Request Packet fields
	 */
	public static final int DATA_AND_REQUEST_PACKET_HEADER_VALUE = 0;
	public static final int DATA_AND_REQUEST_PACKET_LENGTH = 4;
	public static final int DATA_AND_REQUEST_DEST_ENDPOINT_INDEX = 1;
	public static final int DATA_AND_REQUEST_SRC_ENDPOINT_INDEX = 2;
	public static final int DATA_AND_REQUEST_DATA_AND_REQUEST_FOOTER_INDEX = 3;
	
	/*
	 * Response Packet fields
	 */
	public static final int RESPONSE_PACKET_HEADER_VALUE = 1;
	public static final int RESPONSE_PACKET_LENGTH = 11;
	public static final int RESPONSE_PACKET_IN_PORT_INDEX = 1;
	public static final int RESPONSE_PACKET_IN_PORT_LENGTH = 4;
	public static final int RESPONSE_PACKET_OUT_PORT_INDEX = 5;
	public static final int RESPONSE_PACKET_OUT_PORT_LENGTH = 4;
	public static final int RESPONSE_PACKET_DEST_ENDPOINT_INDEX = 9;
	public static final int RESPONSE_PACKET_FOOTER_INDEX = 10;
	
	/*
	 * Router to Controller Connection Info Packet fields
	 */
	public static final int RTOC_INFO_PACKET_HEADER_VALUE = 2;
	public static final int RTOC_INFO_PACKET_MAX_LENGTH = 515;
	public static final int RTOC_INFO_PACKET_ROUTER_ID_INDEX = 1;
	public static final int RTOC_INFO_PACKET_ENDPOINT_CONNS_DATA_LENGTH_INDEX = 2;
	public static final int RTOC_INFO_PACKET_ENDPOINT_CONNS_DATA_INDEX = 3;
	public static final int RTOC_INFO_PACKET_FOOTER_INDEX = 514;
}
