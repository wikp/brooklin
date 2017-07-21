package com.linkedin.datastream.server.dms;

import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.datastream.common.Datastream;
import com.linkedin.datastream.common.DatastreamAlreadyExistsException;
import com.linkedin.datastream.common.DatastreamException;
import com.linkedin.datastream.common.DatastreamStatus;
import com.linkedin.datastream.common.DatastreamUtils;
import com.linkedin.datastream.common.zk.ZkClient;
import com.linkedin.datastream.server.CachedDatastreamReader;
import com.linkedin.datastream.server.zk.KeyBuilder;


public class ZookeeperBackedDatastreamStore implements DatastreamStore {

  private static final Logger LOG = LoggerFactory.getLogger(ZookeeperBackedDatastreamStore.class.getName());

  private final ZkClient _zkClient;
  private final String _cluster;
  private final CachedDatastreamReader _datastreamCache;

  public ZookeeperBackedDatastreamStore(CachedDatastreamReader datastreamCache, ZkClient zkClient, String cluster) {
    assert zkClient != null;
    assert cluster != null;
    assert datastreamCache != null;

    _datastreamCache = datastreamCache;
    _zkClient = zkClient;
    _cluster = cluster;
  }

  private String getZnodePath(String key) {
    return KeyBuilder.datastream(_cluster, key);
  }

  private List<String> getInstances() {
    return _zkClient.getChildren(KeyBuilder.liveInstances(_cluster));
  }

  @Override
  public Datastream getDatastream(String key) {
    if (key == null) {
      return null;
    }
    String path = getZnodePath(key);
    String json = _zkClient.readData(path, true /* returnNullIfPathNotExists */);
    if (json == null) {
      return null;
    }
    return DatastreamUtils.fromJSON(json);
  }

  /**
   * Retrieves all the datastreams in the store. Since there may be many datastreams, it is better
   * to return a Stream and enable further filtering and transformation rather that just a List.
   *
   * The datastream key-set used to make this call is cached, it is possible to get a slightly outdated
   * list of datastreams and not have a stream that was just added. It depends on how long it takes for
   * ZooKeeper to notify the change.
   *
   * @return
   */
  @Override
  public Stream<String> getAllDatastreams() {
    return _datastreamCache.getAllDatastreamNames().stream().sorted();
  }

  @Override
  public void updateDatastream(String key, Datastream datastream) throws DatastreamException {
    // Updating a Datastream is still tricky for now. Changing either the
    // the source or target may result in failure on connector.
    // We only support changes of the "status field"

    Datastream oldDatastream = getDatastream(key);
    if (oldDatastream == null) {
      throw new DatastreamException("Datastream does not exists, can not be updated: " + key);
    }

    oldDatastream.setStatus(datastream.getStatus());

    if (!datastream.equals(oldDatastream)) {
      throw new DatastreamException("Only changes to the 'status' field are supported at this time.");
    }

    String json = DatastreamUtils.toJSON(datastream);
    _zkClient.writeData(getZnodePath(key), json);
    notifyLeaderOfDataChange();
  }

  @Override
  public void createDatastream(String key, Datastream datastream) {
    Validate.notNull(datastream, "null datastream");
    Validate.notNull(key, "null key for datastream" + datastream);

    String path = getZnodePath(key);
    if (_zkClient.exists(path)) {
      String content = _zkClient.ensureReadData(path);
      String errorMessage = String.format("Datastream already exists: path=%s, content=%s", key, content);
      LOG.warn(errorMessage);
      throw new DatastreamAlreadyExistsException(errorMessage);
    }
    _zkClient.ensurePath(path);
    String json = DatastreamUtils.toJSON(datastream);
    _zkClient.writeData(path, json);
  }

  @Override
  public void deleteDatastream(String key) {
    Validate.notNull(key, "null key");

    Datastream datastream = getDatastream(key);
    if (datastream != null) {
      datastream.setStatus(DatastreamStatus.DELETING);
      String data = DatastreamUtils.toJSON(datastream);
      String path = getZnodePath(key);
      _zkClient.updateDataSerialized(path, old -> data);
      notifyLeaderOfDataChange();
    }
  }

  private void notifyLeaderOfDataChange() {
    String dmsPath = KeyBuilder.datastreams(_cluster);
    // Update the /dms to notify that coordinator needs to act on a deleted or changed datastream.
    _zkClient.updateDataSerialized(dmsPath, old -> String.valueOf(System.currentTimeMillis()));
  }
}
