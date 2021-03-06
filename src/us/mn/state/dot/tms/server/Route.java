/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2007-2013  Minnesota Department of Transportation
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
package us.mn.state.dot.tms.server;

import java.io.PrintStream;
import java.util.LinkedList;
import us.mn.state.dot.tms.units.Distance;
import us.mn.state.dot.tms.units.Interval;

/**
 * A route is a list of "corridor trips" from an origin to a destination on a
 * roadway network.
 *
 * @author Douglas Lau
 */
public class Route implements Comparable<Route> {

	/** Penalty (in goodness) for each trip in a route */
	static private final float TRIP_PENALTY = 0.25f;

	/** Name for route debugging */
	private final String name;

	/** List of corridor trips */
	protected final LinkedList<CorridorTrip> trips =
		new LinkedList<CorridorTrip>();

	/** Number of turns in route */
	protected int turns;

	/** Create a new route */
	public Route(String n) {
		name = n;
		turns = 0;
	}

	/** Add a corridor trip to the route */
	public void addTrip(CorridorTrip trip) {
		trips.add(trip);
	}

	/** Set the number of turns in the route */
	public void setTurns(int t) {
		turns = t;
	}

	/** Get the number of turns in the route */
	public int getTurns() {
		return turns;
	}

	/** Get the "only" corridor (if the route is just a single corridor) */
	public Corridor getOnlyCorridor() {
		if(trips.size() == 1)
			return trips.getFirst().getCorridor();
		else
			return null;
	}

	/** Get the route distance.
	 * @return Total route distance. */
	public Distance getDistance() {
		Distance d = new Distance(0);
		for(CorridorTrip trip: trips)
			d = d.add(trip.getDistance());
		return d;
	}

	/** Get the goodness rating (lower is better) */
	public float getGoodness() {
		return getDistance().asFloat(Distance.Units.MILES) +
			TRIP_PENALTY * trips.size();
	}

	/** Compare to another route (for sorting) */
	public int compareTo(Route o) {
		return (int)Math.signum(getGoodness() - o.getGoodness());
	}

	/** Get the current travel time */
	public Interval getTravelTime(boolean final_dest)
		throws BadRouteException
	{
		if(trips.isEmpty())
			throw new BadRouteException("Route is empty");
		Interval t = new Interval(turns, Interval.Units.MINUTES);
		for(CorridorTrip trip: trips)
			t = t.add(trip.getTravelTime(final_dest));
		if(TravelTime.isLogging())
			TravelTime.log(name +" TRAVEL TIME " + t);
		return t;
	}

	/** Get a string representation of the route */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getDistance());
		sb.append(", ");
		sb.append(getTurns());
		sb.append(" turns, ");
		sb.append(getGoodness());
		sb.append(" goodness, ");
		for(CorridorTrip trip: trips)
			sb.append(trip.toString());
		return sb.toString();
	}
}
