/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2009-2015  Minnesota Department of Transportation
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
package us.mn.state.dot.tms.client.incident;

import java.awt.geom.Point2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import us.mn.state.dot.geokit.Position;
import us.mn.state.dot.geokit.SphericalMercatorPosition;
import us.mn.state.dot.map.PointSelector;
import us.mn.state.dot.map.Symbol;
import us.mn.state.dot.tms.CorridorBase;
import us.mn.state.dot.tms.EventType;
import us.mn.state.dot.tms.GeoLoc;
import us.mn.state.dot.tms.GeoLocHelper;
import us.mn.state.dot.tms.Incident;
import us.mn.state.dot.tms.IncidentDetail;
import static us.mn.state.dot.tms.IncidentImpact.FREE_FLOWING;
import us.mn.state.dot.tms.ItemStyle;
import us.mn.state.dot.tms.LaneType;
import us.mn.state.dot.tms.R_Node;
import us.mn.state.dot.tms.R_NodeType;
import us.mn.state.dot.tms.Road;
import us.mn.state.dot.tms.client.IrisClient;
import us.mn.state.dot.tms.client.Session;
import us.mn.state.dot.tms.client.proxy.ProxySelectionListener;
import us.mn.state.dot.tms.client.proxy.ProxySelectionModel;
import us.mn.state.dot.tms.client.proxy.ProxyTheme;
import us.mn.state.dot.tms.client.roads.R_NodeManager;
import us.mn.state.dot.tms.client.widget.ILabel;
import us.mn.state.dot.tms.utils.I18N;

/**
 * GUI for creating new incidents.  These incidents are created as "client"
 * incidents, which may be edited before sending to the server.
 *
 * @author Douglas Lau
 */
public class IncidentCreator extends JPanel {

	/** Button to create a "crash" incident */
	private final JToggleButton crash_btn;

	/** Button to create a "stall" incident */
	private final JToggleButton stall_btn;

	/** Button to create a "road work" incident */
	private final JToggleButton work_btn;

	/** Button to create a "hazard" incident */
	private final JToggleButton hazard_btn;

	/** Lane type combo box */
	private final JComboBox ltype_cbx;

	/** Incident selection model */
	private final ProxySelectionModel<Incident> sel_model;

	/** R_Node manager */
	private final R_NodeManager r_node_manager;

	/** Iris client */
	private final IrisClient client;

	/** Listener for proxy selection events */
	private final ProxySelectionListener sel_listener =
		new ProxySelectionListener()
	{
		public void selectionChanged() {
			if (sel_model.getSelectedCount() > 0)
				clearWidgets();
		}
	};

	/** Create a new incident creator */
	public IncidentCreator(Session s, ProxyTheme<Incident> theme,
		ProxySelectionModel<Incident> sm)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		sel_model = sm;
		r_node_manager = s.getR_NodeManager();
		client = s.getDesktop().client;
		setBorder(BorderFactory.createTitledBorder(
			I18N.get("incident.create")));
		crash_btn = createButton(ItemStyle.CRASH,
			EventType.INCIDENT_CRASH, theme);
		stall_btn = createButton(ItemStyle.STALL,
			EventType.INCIDENT_STALL, theme);
		work_btn = createButton(ItemStyle.ROADWORK,
			EventType.INCIDENT_ROADWORK, theme);
		hazard_btn = createButton(ItemStyle.HAZARD,
			EventType.INCIDENT_HAZARD, theme);
		ltype_cbx = createLaneTypeCombo();
		Box box = Box.createHorizontalBox();
		box.add(crash_btn);
		box.add(Box.createHorizontalStrut(4));
		box.add(stall_btn);
		box.add(Box.createHorizontalStrut(4));
		box.add(work_btn);
		box.add(Box.createHorizontalStrut(4));
		box.add(hazard_btn);
		add(box);
		add(Box.createVerticalStrut(4));
		box = Box.createHorizontalBox();
		box.add(Box.createHorizontalGlue());
		box.add(new ILabel("incident.lane_type"));
		box.add(Box.createHorizontalStrut(4));
		box.add(ltype_cbx);
		box.add(Box.createHorizontalGlue());
		add(box);
		setEnabled(false);
		sel_model.addProxySelectionListener(sel_listener);
	}

	/** Create the lane type combo box */
	private JComboBox createLaneTypeCombo() {
		return new JComboBox(new LaneType[] {
			LaneType.MAINLINE,
			LaneType.EXIT,
			LaneType.MERGE,
			LaneType.CD_LANE
		});
	}

	/** Create a button for creating an incident */
	private JToggleButton createButton(ItemStyle is, final EventType et,
		ProxyTheme<Incident> theme)
	{
		String sty = is.toString();
		Symbol sym = theme.getSymbol(sty);
		final JToggleButton btn = new JToggleButton(sty,
			sym.getLegend());
		btn.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				buttonChanged(btn, et);
			}
		});
		btn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent a) {
				createIncident(btn, et);
			}
		});
		btn.setHorizontalTextPosition(SwingConstants.LEADING);
		return btn;
	}

	/** Handler for button changed events */
	private void buttonChanged(JToggleButton btn, EventType et) {
		if(btn.isSelected()) {
			sel_model.clearSelection();
			// NOTE: cannot use ButtonGroup for this because it
			// will not let the user deselect a button by clicking
			// on it once it has been selected.  Arrgh!
			if(btn != crash_btn && crash_btn.isSelected())
				crash_btn.setSelected(false);
			if(btn != stall_btn && stall_btn.isSelected())
				stall_btn.setSelected(false);
			if(btn != work_btn && work_btn.isSelected())
				work_btn.setSelected(false);
			if(btn != hazard_btn && hazard_btn.isSelected())
				hazard_btn.setSelected(false);
		}
	}

	/** Create a incident */
	private void createIncident(final JToggleButton btn,
		final EventType et)
	{
		assert et == EventType.INCIDENT_CRASH ||
		       et == EventType.INCIDENT_STALL ||
		       et == EventType.INCIDENT_ROADWORK ||
		       et == EventType.INCIDENT_HAZARD;
		client.setPointSelector(new PointSelector() {
			public boolean selectPoint(Point2D p) {
				createIncident(null, et, getPosition(p));
				return true;
			}
			public void finish() {
				btn.setSelected(false);
				setEnabled(true);
			}
		});
	}

	/** Replace an existing incident */
	public void replaceIncident(Incident inc) {
		final String replaces = inc.getName();
		final EventType et = EventType.fromId(inc.getEventType());
		final IncidentDetail dtl = inc.getDetail();
		final LaneType lt = LaneType.fromOrdinal(inc.getLaneType());
		client.setPointSelector(new PointSelector() {
			public boolean selectPoint(Point2D p) {
				createIncident(replaces, et, dtl, lt,
					getPosition(p));
				return true;
			}
			public void finish() { }
		});
	}

	/** Get a spherical mercator position */
	private SphericalMercatorPosition getPosition(Point2D p) {
		return new SphericalMercatorPosition(p.getX(), p.getY());
	}

	/** Create an incident */
	private void createIncident(String replaces, EventType et,
		SphericalMercatorPosition smp)
	{
		LaneType lt = (LaneType)ltype_cbx.getSelectedItem();
		if(lt != null) {
			createIncident(replaces, et, (IncidentDetail)null, lt,
				smp);
		}
	}

	/** Create an incident */
	private void createIncident(String replaces, EventType et,
		IncidentDetail dtl, LaneType lt, SphericalMercatorPosition smp)
	{
		GeoLoc loc = r_node_manager.createGeoLoc(smp,
			lt == LaneType.CD_LANE);
		if(loc != null)
			createIncident(replaces, et, dtl, lt, loc);
	}

	/** Create an incident */
	private void createIncident(String replaces, EventType et,
		IncidentDetail dtl, LaneType lt, GeoLoc loc)
	{
		loc = snapGeoLoc(lt, loc);
		Road road = loc.getRoadway();
		short dir = loc.getRoadDir();
		Position pos = GeoLocHelper.getWgs84Position(loc);
		int n_lanes = getLaneCount(lt, loc);
		if(pos != null && n_lanes > 0) {
			ClientIncident ci = new ClientIncident(replaces, et.id,
				dtl, (short)lt.ordinal(), road, dir,
				(float)pos.getLatitude(),
				(float)pos.getLongitude(),
				createImpact(n_lanes));
			sel_model.setSelected(ci);
		}
	}

	/** Snap a location to the proper lane type */
	private GeoLoc snapGeoLoc(LaneType lt, GeoLoc loc) {
		CorridorBase cb = r_node_manager.lookupCorridor(loc);
		if(cb == null)
			return loc;
		Position pos = GeoLocHelper.getWgs84Position(loc);
		if(pos == null)
			return loc;
		switch(lt) {
		case EXIT:
			R_Node n = cb.findNearest(pos, R_NodeType.EXIT);
			if(n != null)
				return n.getGeoLoc();
			else
				return loc;
		case MERGE:
			R_Node mn = cb.findNearest(pos, R_NodeType.ENTRANCE);
			if(mn != null)
				return mn.getGeoLoc();
			else
				return loc;
		default:
			return loc;
		}
	}

	/** Get the lane count at the incident location */
	private int getLaneCount(LaneType lt, GeoLoc loc) {
		CorridorBase cb = r_node_manager.lookupCorridor(loc);
		Position pos = GeoLocHelper.getWgs84Position(loc);
		if(pos == null)
			return 0;
		switch(lt) {
		case EXIT:
			R_Node n = cb.findNearest(pos, R_NodeType.EXIT);
			if(n != null)
				return n.getLanes();
			else
				return 0;
		case MERGE:
			R_Node mn = cb.findNearest(pos, R_NodeType.ENTRANCE);
			if(mn != null)
				return mn.getLanes();
			else
				return 0;
		default:
			return cb.laneConfiguration(pos).getLanes();
		}
	}

	/** Create an impact string for the given number of lanes */
	private String createImpact(int n_lanes) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < n_lanes + 2; i++)
			sb.append(FREE_FLOWING._char);
		return sb.toString();
	}

	/** Get the selected toggle button */
	private JToggleButton getSelected() {
		if(crash_btn.isSelected())
			return crash_btn;
		if(stall_btn.isSelected())
			return stall_btn;
		if(work_btn.isSelected())
			return work_btn;
		if(hazard_btn.isSelected())
			return hazard_btn;
		return null;
	}

	/** Clear the widgets */
	private void clearWidgets() {
		client.setPointSelector(null);
		if(crash_btn.isSelected())
			crash_btn.setSelected(false);
		if(stall_btn.isSelected())
			stall_btn.setSelected(false);
		if(work_btn.isSelected())
			work_btn.setSelected(false);
		if(hazard_btn.isSelected())
			hazard_btn.setSelected(false);
		ltype_cbx.setSelectedItem(LaneType.MAINLINE);
	}

	/** Set enabled */
	public void setEnabled(boolean e) {
		crash_btn.setEnabled(e);
		stall_btn.setEnabled(e);
		work_btn.setEnabled(e);
		hazard_btn.setEnabled(e);
		ltype_cbx.setEnabled(e);
	}

	/** Dispose of the incident creator */
	public void dispose() {
		sel_model.removeProxySelectionListener(sel_listener);
		removeAll();
	}
}
