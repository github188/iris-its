/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2002-2009  Minnesota Department of Transportation
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
package us.mn.state.dot.tms.server.comm.ntcip.mibskyline;

import us.mn.state.dot.tms.server.comm.ntcip.ASN1OctetString;

/**
 * Ntcip SensorFailures object
 *
 * @author Douglas Lau
 */
public class SensorFailures extends ASN1OctetString {

	/** Create a new SensorFailures object */
	public SensorFailures() {
		super(MIB.skylineDmsStatus.create(new int[] {17, 0}));
	}

	/** Get the object value */
	public String getValue() {
		StringBuilder buf = new StringBuilder();
		int f = 1;
		for(int i = 0; i < value.length; i++) {
			for(int b = 0; b < 8; b++, f++) {
				if((value[i] & 1 << b) != 0) {
					if(buf.length() > 0)
						buf.append(", ");
					buf.append("#");
					buf.append(f);
				}
			}
		}
		if(buf.length() == 0)
			buf.append("None");
		return buf.toString();
	}
}
