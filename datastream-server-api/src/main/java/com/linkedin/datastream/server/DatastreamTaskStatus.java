package com.linkedin.datastream.server;

import org.apache.commons.lang.Validate;

/**
 * Represent the status of a DatastreamTask with a code and message.
 */
public class DatastreamTaskStatus {
  public enum Code { OK, ERROR, COMPLETE }

  private Code _code;
  private String _message;

  // Needed for JSON deserialization
  public DatastreamTaskStatus() {
  }

  public DatastreamTaskStatus(Code code, String message) {
    if (code != Code.ERROR) {
      Validate.notEmpty(message, "must provide a message for ERROR status.");
    }
    _code = code;
    _message = message;
  }

  /**
   * Helper method to create an OK status.
   * @return OK task status
   */
  public static DatastreamTaskStatus ok() {
    return new DatastreamTaskStatus(Code.OK, "OK");
  }

  /**
   * Helper method to create an OK status.
   * @param message message to return
   * @return OK task status
   */
  public static DatastreamTaskStatus ok(String message) {
    return new DatastreamTaskStatus(Code.OK, message);
  }

  /**
   * Helper method to create an ERROR status
   * @param message
   * @return ERROR task status
   */
  public static DatastreamTaskStatus error(String message) {
    return new DatastreamTaskStatus(Code.ERROR, message);
  }

  /**
   * Helper method to create a COMPLETE status
   * @return COMPLETE task status
   */
  public static DatastreamTaskStatus complete() {
    return new DatastreamTaskStatus(Code.COMPLETE, "Completed.");
  }

  /**
   * @return kind of the status
   */
  public Code getCode() {
    return _code;
  }

  /**
   * @return message associated with the status
   */
  public String getMessage() {
    return _message;
  }

  /**
   * Set Code of the status. Needed for JsonUtils.
   * @param code status code
   */
  public void setCode(Code code) {
    _code = code;
  }

  /**
   * Set message of the status. Needed for JsonUtils.
   * @param message status message
   */
  public void setMessage(String message) {
    _message = message;
  }

  @Override
  public String toString() {
    return String.format("TaskStatus: code=%s, msg=%s", _code, _message);
  }
}
