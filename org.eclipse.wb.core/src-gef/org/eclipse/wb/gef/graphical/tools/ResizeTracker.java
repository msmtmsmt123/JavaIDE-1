/*******************************************************************************
 * Copyright (c) 2011 Google, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Google, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.wb.gef.graphical.tools;

import com.google.common.collect.Lists;

import org.eclipse.wb.draw2d.ICursorConstants;
import org.eclipse.wb.draw2d.IPositionConstants;
import org.eclipse.wb.draw2d.geometry.Dimension;
import org.eclipse.wb.draw2d.geometry.Point;
import org.eclipse.wb.gef.core.Command;
import org.eclipse.wb.gef.core.EditPart;
import org.eclipse.wb.gef.core.IEditPartViewer;
import org.eclipse.wb.gef.core.requests.ChangeBoundsRequest;
import org.eclipse.wb.gef.core.requests.KeyRequest;
import org.eclipse.wb.gef.core.requests.Request;
import org.eclipse.wb.gef.core.tools.Tool;
import org.eclipse.wb.internal.gef.core.CompoundCommand;

import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Cursor;

import java.util.List;
import java.util.ListIterator;

/**
 * A tracker for dragging a resize handle. The {@link ResizeTracker} will resize all of the selected
 * editparts in the viewer which understand request specified into constructor. A
 * {@link ChangeBoundsRequest} is sent to each member of the operation set. The tracker allows for
 * the resize direction to be specified in the constructor.
 * 
 * @author lobas_av
 * @coverage gef.graphical
 */
public class ResizeTracker extends Tool {
  private final List<EditPart> m_operationSet;
  private final int m_direction;
  private final Object m_requestType;
  private ChangeBoundsRequest m_request;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Constructors
  //
  ////////////////////////////////////////////////////////////////////////////
  public ResizeTracker(int direction, Object requestType) {
    this(null, direction, requestType);
  }

  public ResizeTracker(EditPart selectionEditPart, int direction, Object requestType) {
    if (selectionEditPart == null) {
      m_operationSet = null;
    } else {
      m_operationSet = Lists.newArrayList();
      m_operationSet.add(selectionEditPart);
    }
    m_direction = direction;
    m_requestType = requestType;
    setDefaultCursor(ICursorConstants.Directional.getCursor(direction));
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Access
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * @return the type of {@link Request} that is generated by this {@link ResizeTracker}. For tests.
   */
  public Object getRequestType() {
    return m_requestType;
  }

  /**
   * @return the resize direction. For tests.
   */
  public int getDirection() {
    return m_direction;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Tool
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * Add erases source feedback.
   */
  @Override
  public void deactivate() {
    eraseSourceFeedback();
    setRequest(null);
    super.deactivate();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Cursor
  //
  ////////////////////////////////////////////////////////////////////////////
  @Override
  protected Cursor calculateCursor() {
    if (m_state == STATE_DRAG) {
      return getDefaultCursor();
    }
    return super.calculateCursor();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // High-Level handle MouseEvent
  //
  ////////////////////////////////////////////////////////////////////////////
  @Override
  protected void handleButtonDown(int button) {
    if (button == 1) {
      if (m_state == STATE_INIT) {
        m_state = STATE_DRAG;
      }
    } else {
      m_state = STATE_INVALID;
      eraseSourceFeedback();
      setCommand(null);
    }
  }

  @Override
  protected void handleButtonUp(int button) {
    if (m_state == STATE_DRAG_IN_PROGRESS) {
      m_state = STATE_NONE;
      eraseSourceFeedback();
      executeCommand();
    }
  }

  @Override
  protected void handleDragStarted() {
    if (m_state == STATE_DRAG) {
      m_state = STATE_DRAG_IN_PROGRESS;
    }
  }

  @Override
  protected void handleDragInProgress() {
    if (m_state == STATE_DRAG_IN_PROGRESS) {
      updateRequest();
      showSourceFeedback();
      updateCommand();
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Handle KeyEvent
  //
  ////////////////////////////////////////////////////////////////////////////
  @Override
  public void keyPressed(KeyEvent event, IEditPartViewer viewer) {
    sendKeyRequest(true, event);
  }

  @Override
  public void keyReleased(KeyEvent event, IEditPartViewer viewer) {
    sendKeyRequest(false, event);
  }

  /**
   * Sends the {@link KeyRequest} to the {@link EditPart}'s in operation set.
   */
  private void sendKeyRequest(boolean pressed, KeyEvent event) {
    KeyRequest request = new KeyRequest(pressed, event);
    for (EditPart editPart : createOperationSet()) {
      editPart.performRequest(request);
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Request/Command
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * Returns all selected parts which understand resizing.
   */
  @Override
  protected List<EditPart> createOperationSet() {
    if (m_operationSet != null) {
      return m_operationSet;
    }
    // find target EditPart's that agree to process current request
    List<EditPart> operationSet = super.createOperationSet();
    for (ListIterator<EditPart> I = operationSet.listIterator(); I.hasNext();) {
      EditPart part = I.next();
      // find target for current EditPart
      while (part != null) {
        EditPart newPart = part.getTargetEditPart(getRequest());
        if (newPart == part) {
          break;
        }
        part = newPart;
      }
      // if current EditPart can not provide target, remove it
      if (part == null) {
        I.remove();
        continue;
      }
      // use target EditPart
      I.set(part);
    }
    //
    return operationSet;
  }

  /**
   * Updates the request with the current {@link Tool#getOperationSet() operation set}, move delta,
   * size delta and location.
   */
  private void updateRequest() {
    // create request
    if (getRequest() == null) {
      setRequest(new ChangeBoundsRequest(m_requestType));
      getRequest().setResizeDirection(m_direction);
    }
    // set EditPart's
    getRequest().setEditParts(getOperationSet());
    // set stateMask
    getRequest().setStateMask(m_stateMask);
    // update request
    Point corner = new Point();
    Dimension resize = new Dimension();
    Dimension dragMoveDelta = getDragMoveDelta();
    // calculate vertical move and size delta
    if ((m_direction & IPositionConstants.NORTH) != 0) {
      corner.y += dragMoveDelta.height;
      resize.height -= dragMoveDelta.height;
    } else if ((m_direction & IPositionConstants.SOUTH) != 0) {
      resize.height += dragMoveDelta.height;
    }
    // calculate horizontal move and size delta
    if ((m_direction & IPositionConstants.WEST) != 0) {
      corner.x += dragMoveDelta.width;
      resize.width -= dragMoveDelta.width;
    } else if ((m_direction & IPositionConstants.EAST) != 0) {
      resize.width += dragMoveDelta.width;
    }
    // set request data
    getRequest().setMoveDelta(corner);
    getRequest().setSizeDelta(resize);
    getRequest().setLocation(getLocation());
  }

  /**
   * Asks each edit part in the {@link Tool#getOperationSet() operation set} to contribute to a
   * {@link CompoundCommand}.
   */
  @Override
  protected Command getCommand() {
    CompoundCommand command = new CompoundCommand();
    //
    for (EditPart part : getOperationSet()) {
      command.add(part.getCommand(getRequest()));
    }
    //
    return command.unwrap();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Feedback
  //
  ////////////////////////////////////////////////////////////////////////////
  private boolean m_isShowingFeedback;

  /**
   * Asks the edit parts in the {@link Tool#getOperationSet() operation set} to show source
   * feedback.
   */
  protected void showSourceFeedback() {
    for (EditPart part : getOperationSet()) {
      part.showSourceFeedback(getRequest());
    }
    setShowingFeedback(true);
  }

  /**
   * Asks the edit parts in the {@link Tool#getOperationSet() operation set} to erase their source
   * feedback.
   */
  protected void eraseSourceFeedback() {
    if (isShowingFeedback()) {
      setShowingFeedback(false);
      for (EditPart part : getOperationSet()) {
        part.eraseSourceFeedback(getRequest());
      }
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Access
  //
  ////////////////////////////////////////////////////////////////////////////
  protected final void setShowingFeedback(boolean isShowingFeedback) {
    m_isShowingFeedback = isShowingFeedback;
  }

  protected final boolean isShowingFeedback() {
    return m_isShowingFeedback;
  }

  private void setRequest(ChangeBoundsRequest request) {
    m_request = request;
  }

  protected final ChangeBoundsRequest getRequest() {
    return m_request;
  }
}