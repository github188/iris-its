/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2000-2014  Minnesota Department of Transportation
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package us.mn.state.dot.tms.server.comm.ntcip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedList;
import us.mn.state.dot.sched.DebugLog;
import us.mn.state.dot.tms.server.comm.CommMessage;
import us.mn.state.dot.tms.server.comm.ControllerProperty;
import us.mn.state.dot.tms.server.comm.ParsingException;

/**
 * Simple Network Management Protocol (SNMP)
 *
 * @author Douglas Lau
 */
public class SNMP extends BER {

	/** SNMP debug log */
	static private final DebugLog SNMP_LOG = new DebugLog("snmp");

 	/** SNMP error status codes */
	static protected final int NO_ERROR = 0;
	static protected final int TOO_BIG = 1;
	static protected final int NO_SUCH_NAME = 2;
	static protected final int BAD_VALUE = 3;
	static protected final int READ_ONLY = 4;
	static protected final int GEN_ERROR = 5;

	/** SNMP Identifier tag */
	static public class Tag extends ASN1.Tag {

		/** Create a new SNMP identifier tag */
		protected Tag(byte c, boolean co, int n) {
			super(c, co, n);
		}

		/** Get-request tag */
		static public final Tag GET_REQUEST =
			new Tag(CONTEXT, true, 0);

		/** Get-next-request tag */
		static public final Tag GET_NEXT_REQUEST =
			new Tag(CONTEXT, true, 1);

		/** Get-response tag */
		static public final Tag GET_RESPONSE =
			new Tag(CONTEXT, true, 2);

		/** Set-request tag */
		static public final Tag SET_REQUEST =
			new Tag(CONTEXT, true, 3);

		/** Trap tag */
		static public final Tag TRAP = new Tag(CONTEXT, true, 4);

		/** Counter tag (RFC1155-SMI) */
		static public final Tag COUNTER = new Tag(APPLICATION, false,1);
	}

	/** Get a tag that matches */
	protected ASN1.Tag getTag(byte clazz, boolean constructed, int number) {
		Tag tag = new Tag(clazz, constructed, number);
		if(tag.equals(Tag.GET_REQUEST))
			return Tag.GET_REQUEST;
		if(tag.equals(Tag.GET_NEXT_REQUEST))
			return Tag.GET_NEXT_REQUEST;
		if(tag.equals(Tag.GET_RESPONSE))
			return Tag.GET_RESPONSE;
		if(tag.equals(Tag.SET_REQUEST))
			return Tag.SET_REQUEST;
		if(tag.equals(Tag.TRAP))
			return Tag.TRAP;
		if(tag.equals(Tag.COUNTER))
			return Tag.COUNTER;
		return super.getTag(clazz, constructed, number);
	}

	/** SNMP version number */
	static public final int SNMP_VERSION = 0;

	/** Public community name */
	static public final String PUBLIC = "Public";

	/** Ledstar firmware bug workaround. Instead of 128,129,130,..., it
	 * returns -128,-127,-126,... */
	static protected final int REQUEST_ID_MAX_LEDSTAR_BUG = 127;

	/** Last SNMP request-id */
	protected int last_request = 0;

	/** Encode an SNMP message */
	protected void encodeSNMPMessage(String community) throws IOException {
		byte[] pdu = getEncodedData();
		encodeInteger(SNMP_VERSION);
		encodeOctetString(community.getBytes());
		encoder.write(pdu);
		encodeSequence(getEncodedData());
	}

	/** Decode an SNMP message */
	protected void decodeSNMPMessage(InputStream is, String community)
		throws IOException
	{
		if(decodeSequence(is) > is.available())
			throw new ParsingException("INVALID SNMP LENGTH");
		if(decodeInteger(is) != 0)
			throw new ParsingException("SNMP VERSION MISMATCH");
		String c = new String(decodeOctetString(is));
		if(!c.equals(community))
			throw new ParsingException("SNMP COMMUNITY MISMATCH");
	}

	/** SNMP message class */
	public class Message implements CommMessage {

		/** Output stream for this message */
		protected final OutputStream os;

		/** Input stream for this message */
		protected final InputStream is;

		/** Community name */
		protected final String community;

		/** SNMP request-id */
		public final int request_id;

		/** List of objects set or get with this message */
		protected final LinkedList<ASN1Object> mos =
			new LinkedList<ASN1Object>();

		/** Create a new SNMP message */
		public Message(OutputStream o, InputStream i, String c) {
			os = o;
			is = i;
			community = (c != null) ? c : PUBLIC;
			request_id = last_request++;
			if(last_request > REQUEST_ID_MAX_LEDSTAR_BUG)
				last_request = 0;
		}

		/** Add a controller property */
		public void add(ControllerProperty cp) {
			if(cp instanceof ASN1Object)
				mos.add((ASN1Object)cp);
		}

		/** Query the controller properties.  This is accomplished with
		 * an SNMP get-request.
		 * @throws IOException On any errors sending a request or
		 *         receiving response */
		public void queryProps() throws IOException {
			if(mos.isEmpty())
				return;
			is.skip(is.available());
			encodeVarBindList(false);
			encodeRequestPDU(Tag.GET_REQUEST);
			encodeSNMPMessage(community);
			encoder.writeTo(os);
			encoder.reset();
			os.flush();
			decodeResponse();
		}

		/** Store the controller properties.  This is accomplished with
		 * an SNMP set-request.
		 * @throws IOException On any errors sending a request or
		 *         receiving response */
		public void storeProps() throws IOException {
			if(mos.isEmpty())
				return;
			is.skip(is.available());
			encodeVarBindList(true);
			encodeRequestPDU(Tag.SET_REQUEST);
			encodeSNMPMessage(community);
			encoder.writeTo(os);
			encoder.reset();
			os.flush();
			decodeResponse();
		}

		/** Decode a response to a SET or GET request */
		protected void decodeResponse() throws IOException {
			for(int i = 0;; i++) {
				try {
					decodeSNMPMessage(is, community);
					decodeResponsePDU(is);
					decodeVarBindList(is);
					return;
				}
				catch(RequestIDException e) {
					SNMP_LOG.log(e.getMessage());
					is.skip(is.available());
					if(i >= 5)
						throw e;
				}
			}
		}

		/** Encode the value of an MIB object */
		protected void encodeValue(ASN1Object mo) throws IOException {
			if(mo instanceof ASN1Integer) {
				ASN1Integer value = (ASN1Integer)mo;
				encodeInteger(value.getInteger());
			} else if(mo instanceof ASN1OctetString) {
				ASN1OctetString value = (ASN1OctetString)mo;
				encodeOctetString(value.getOctetString());
			} else
				throw new IOException("UNKNOWN OBJECT TYPE");
		}

		/** Encode a null variable binding */
		protected void encodeVarBind(ASN1Object mo, boolean set)
			throws IOException
		{
			encodeObjectIdentifier(mo.getOID());
			if(set)
				encodeValue(mo);
			else
				encodeNull();
			encodeSequence(getEncodedData());
		}

		/** Encode the variable binding list */
		protected void encodeVarBindList(boolean set)
			throws IOException
		{
			ByteArrayOutputStream vb = new ByteArrayOutputStream();
			for(ASN1Object mo: mos) {
				encodeVarBind(mo, set);
				vb.write(getEncodedData());
			}
			encodeSequence(vb.toByteArray());
		}

		/** Encode an SNMP request PDU
		 * @param tag PDU type identifier */
		protected void encodeRequestPDU(Tag tag) throws IOException {
			byte[] varBindList = getEncodedData();
			encodeInteger(request_id);
			encodeInteger(0);	// error-status
			encodeInteger(0);	// error-index
			encoder.write(varBindList);
			byte[] buffer = getEncodedData();
			encodeIdentifier(tag);
			encodeLength(buffer.length);
			encoder.write(buffer);
		}

		/** Decode the value of an MIB object */
		protected void decodeValue(InputStream is, ASN1Object mo)
			throws IOException
		{
			if(mo instanceof Counter) {
				Counter value = (Counter)mo;
				value.setInteger(decodeCounter(is));
			} else if(mo instanceof ASN1Integer) {
				ASN1Integer value = (ASN1Integer)mo;
				value.setInteger(decodeInteger(is));
			} else if(mo instanceof ASN1OctetString) {
				ASN1OctetString value = (ASN1OctetString)mo;
				value.setOctetString(decodeOctetString(is));
			} else
				throw new IOException("UNKNOWN OBJECT TYPE");
		}

		/** Decode a counter (from RFC1155-SMI) */
		protected int decodeCounter(InputStream is) throws IOException {
			if(decodeIdentifier(is) != Tag.COUNTER)
				throw new ParsingException("EXPECTED COUNTER");
			int length = decodeLength(is);
			if(length < 1 || length > 4) {
				throw new ParsingException(
					"INVALID COUNTER LENGTH");
			}
			int value = is.read();
			if(value < 0)
				throw END_OF_STREAM;
			for(int i = 1; i < length; i++) {
				value <<= 8;
				int v = is.read();
				if(v < 0)
					throw END_OF_STREAM;
				value |= v;
			}
			return value;
		}

		/** Decode a variable binding */
		protected void decodeVarBind(InputStream is, ASN1Object mo)
			throws IOException
		{
			decodeSequence(is);
			// FIXME: compare with OID from mo
			decodeObjectIdentifier(is);
			decodeValue(is, mo);
		}

		/** Decode the variable binding list */
		protected void decodeVarBindList(InputStream is)
			throws IOException
		{
			decodeSequence(is);
			for(ASN1Object mo: mos)
				decodeVarBind(is, mo);
		}

		/** Decode an SNMP response PDU */
		protected void decodeResponsePDU(InputStream is)
			throws IOException
		{
			if(decodeIdentifier(is) != Tag.GET_RESPONSE)
				throw new ParsingException("!GET_RESPONSE TAG");
			if(decodeLength(is) > is.available())
				throw new ParsingException("INVALID PDU LEN");
			int request = decodeInteger(is);
			if(request != request_id)
				throw new RequestIDException(request);
			int error = decodeInteger(is);
			int index = decodeInteger(is);
			switch(error) {
			case TOO_BIG:
				throw new TooBig();
			case NO_SUCH_NAME:
				throw new NoSuchName(index);
			case BAD_VALUE:
				throw new BadValue(index);
			case READ_ONLY:
				throw new ReadOnly(index);
			case GEN_ERROR:
				throw new GenError(index);
			}
		}

		/** Request ID mismatch exception */
		public class RequestIDException extends ParsingException {

			/** Create a new Request-ID exception */
			protected RequestIDException(int request) {
				super("SNMP REQUEST ID: " + request + " != " +
					request_id);
			}
		}

		/** TooBig exception */
		public class TooBig extends IOException {

			/** Create a new TooBig exception */
			protected TooBig() {}

			/** Get the error message string */
			public String getMessage() {
				return "tooBig";
			}
		}

		/** NoSuchName exception */
		public class NoSuchName extends IOException {

			/** Object name */
			protected final String name;

			/** Create a new NoSuchName exception */
			protected NoSuchName(int i) {
				name = (mos.get(i - 1)).getName();
			}

			/** Get the error message string */
			public String getMessage() {
				return "noSuchName: " + name;
			}
		}

		/** BadValue exception */
		public class BadValue extends IOException {

			/** MIB Object */
			protected final ASN1Object obj;

			/** Create a new BadValue exception */
			protected BadValue(int i) {
				obj = mos.get(i - 1);
			}

			/** Get the error message string */
			public String getMessage() {
				return "badValue: " + obj;
			}
		}

		/** ReadOnly exception */
		public class ReadOnly extends IOException {

			/** Object name */
			protected final String name;

			/** Create a new ReadOnly exception */
			protected ReadOnly(int i) {
				name = (mos.get(i - 1)).getName();
			}

			/** Get the error message string */
			public String getMessage() {
				return "readOnly: " + name;
			}
		}

		/** GenError exception */
		public class GenError extends IOException {

			/** MIB Object */
			protected final ASN1Object obj;

			/** Create a new GenError exception */
			protected GenError(int i) {
				if(i > 0)
					obj = mos.get(i - 1);
				else
					obj = null;
			}

			/** Get the error message string */
			public String getMessage() {
				return "genError: " + obj;
			}
		}
	}
}
