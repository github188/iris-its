/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2008-2012  Minnesota Department of Transportation
 * Copyright (C) 2010  AHMCT, University of California
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
package us.mn.state.dot.tms;

import java.io.IOException;
import java.util.Iterator;

/**
 * Helper for dealing with sign messages.
 *
 * @author Douglas Lau
 * @author Michael Darter
 */
public class SignMessageHelper extends BaseHelper {

	/** Maximum allowed pages for any message */
	static public final int DMS_MESSAGE_MAX_PAGES = 6;

	/** Do not allow objects of this class */
	private SignMessageHelper() {
		assert false;
	}

	/** Lookup the sign message with the specified name */
	static public SignMessage lookup(String name) {
		return (SignMessage)namespace.lookupObject(
			SignMessage.SONAR_TYPE, name);
	}

	/** Get a sign message iterator */
	static public Iterator<SignMessage> iterator() {
		return new IteratorWrapper<SignMessage>(namespace.iterator(
			SignMessage.SONAR_TYPE));
	}

	/** Find a sign message with matching attributes */
	static public SignMessage find(String multi, String bitmaps,
		DMSMessagePriority ap, DMSMessagePriority rp, boolean s,
		Integer d)
	{
		int api = ap.ordinal();
		int rpi = rp.ordinal();
		Iterator<SignMessage> it = iterator();
		while(it.hasNext()) {
			SignMessage sm = it.next();
			if(multi.equals(sm.getMulti()) &&
			   bitmaps.equals(sm.getBitmaps()) &&
			   api == sm.getActivationPriority() &&
			   rpi == sm.getRunTimePriority() &&
			   s == sm.getScheduled() &&
			   integerEquals(d, sm.getDuration()))
				return sm;
		}
		return null;
	}

	/** Compare two (possibly-null) integers for equality */
	static protected boolean integerEquals(Integer i0, Integer i1) {
		if(i0 == null)
			return i1 == null;
		else
			return i0.equals(i1);
	}

	/** Compare the attributes of 2 sign messages.
	 * @param sm1 SignMessage which may be null.
	 * @param sm2 SignMessage which may be null.
	 * @return True if sm1 and sm2 have equal MULTIs, priorities, 
	 *         and bitmaps. */
	static public boolean isEquivalent(SignMessage sm1, SignMessage sm2) {
		if(sm1 == null && sm2 == null)
			return true;
		if(sm1 == null || sm2 == null)
			return false;
		if(!new MultiString(sm1.getMulti()).equals(sm2.getMulti()))
			return false;
		if(sm1.getActivationPriority() != sm2.getActivationPriority())
			return false;
		if(sm1.getRunTimePriority() != sm2.getRunTimePriority())
			return false;
		final String bm1 = sm1.getBitmaps();
		final String bm2 = sm2.getBitmaps();
		if(bm1 == null)
			return bm2 == null;
		else
			return bm1.equals(bm2);
	}

	/** Return an array of font names in a message.
	 * @param f_num Default font number, one based.
	 * @return A string array with length equal to the number 
	 *	    of pages in the message */
	static public String[] getFontNames(SignMessage sm, int f_num) {
		int[] fn = getFonts(sm, f_num);
		if(fn == null || fn.length <= 0)
			return new String[0];
		String[] fns = new String[fn.length];
		for(int i=0; i < fns.length; ++i) {
			Font font = FontHelper.find(fn[i]);
			if(font != null)
				fns[i] = font.getName();
			else
				fns[i] = "Font #" + fn[i];
		}
		return fns;
	}

	/** Get an array of font numbers in a message.
	 * @param f_num Default font number, one based.
	 * @return An array of font numbers for each page of the message. */
	static protected int[] getFonts(SignMessage sm, int f_num) {
		if(sm == null)
			return new int[0];
		MultiString m = new MultiString(sm.getMulti());
		return m.getFonts(f_num);
	}

	/** Check if a sign message is blank */
	static public boolean isBlank(SignMessage m) {
		return isMultiBlank(m) && isBitmapBlank(m);
	}

	/** Check if the MULTI string is blank */
	static public boolean isMultiBlank(SignMessage m) {
		String ms = m.getMulti();
		return ms == null || new MultiString(ms).isBlank();
	}

	/** Check if the bitmap is blank */
	static public boolean isBitmapBlank(SignMessage m) {
		byte[] bmaps = decodeBitmaps(m);
		if(bmaps != null) {
			for(byte b: bmaps) {
				if(b != 0)
					return false;
			}
			return true;
		} else
			return false;
	}

	/** Get the bitmap graphic for all pages of the specified DMS.
	 * @param sm SignMessage in question.
	 * @param DMS with the graphic.
	 * @return Array of bitmaps, one for each page, or null on error. */
	static public BitmapGraphic[] getBitmaps(SignMessage sm, DMS dms) {
		if(sm == null || dms == null)
			return null;
		byte[] bmaps = decodeBitmaps(sm);
		if(bmaps == null)
			return null;
		BitmapGraphic bg = DMSHelper.createBitmapGraphic(dms);
		if(bg == null)
			return null;
		int blen = bg.length();
		if(blen == 0 || bmaps.length % blen != 0)
			return null;
		int n_pages = bmaps.length / blen;
		BitmapGraphic[] bitmaps = new BitmapGraphic[n_pages];
		for(int i = 0; i < n_pages; i++) {
			bitmaps[i] = DMSHelper.createBitmapGraphic(dms);
			byte[] b = new byte[blen];
			System.arraycopy(bmaps, i * blen, b, 0, blen);
			bitmaps[i].setPixels(b);
		}
		return bitmaps;
	}

	/** Decode the bitmaps on a sign message */
	static private byte[] decodeBitmaps(SignMessage sm) {
		if(sm != null) {
			String bmaps = sm.getBitmaps();
			if(bmaps != null) {
				try {
					return Base64.decode(bmaps);
				}
				catch(IOException e) {
					// fall through
				}
			}
		}
		return null;
	}
}
