package com.thinkaurelius.titan.diskstorage.cassandra.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.thinkaurelius.titan.diskstorage.StaticBuffer;
import com.thinkaurelius.titan.diskstorage.keycolumnvalue.Entry;
import com.thinkaurelius.titan.diskstorage.keycolumnvalue.KeyRange;
import com.thinkaurelius.titan.diskstorage.util.ByteBufferUtil;
import com.thinkaurelius.titan.diskstorage.util.StaticArrayBuffer;

import org.apache.cassandra.dht.BytesToken;
import org.apache.cassandra.dht.Range;
import org.apache.cassandra.dht.Token;

public class CassandraHelper {
    /**
     * Orders first argument according to key positions in second argument.
     *
     * We need this to ensure that ordering of entries in the result would match ordering of keys,
     * as keys are token sorted in Cassandra.
     *
     * @param toOrder Result of the "multiget_slice" call key => entry(column,value); (potentially ordered differently).
     * @param orderedKeys Keys in the correct order.
     *
     *
     * @return Ordered list of entries.
     */
    public static List<List<Entry>> order(Map<ByteBuffer, List<Entry>> toOrder, List<StaticBuffer> orderedKeys) {
        List<List<Entry>> results = new ArrayList<List<Entry>>();

        // We need this to ensure that ordering of entries in the result would match ordering of keys,
        // as keys are token sorted in Cassandra.
        for (StaticBuffer key : orderedKeys) {
            results.add(toOrder.get(key.asByteBuffer()));
        }

        return results;
    }

    public static KeyRange transformRange(Range<Token> range) {
        return transformRange(range.left, range.right);
    }

    public static KeyRange transformRange(Token<?> leftKeyExclusive, Token<?> rightKeyInclusive) {
        
        if (!(leftKeyExclusive instanceof BytesToken))
            throw new UnsupportedOperationException();

        // if left part is BytesToken, right part should be too, otherwise there is no sense in the ring
        assert rightKeyInclusive instanceof BytesToken;

        // l is exclusive, r is inclusive
        BytesToken l = (BytesToken) leftKeyExclusive;
        BytesToken r = (BytesToken) rightKeyInclusive;
        

        final StaticBuffer lb, rb;
        if (l.equals(r)) {
            /* Special case: if start and end tokens are equal, then only decrement the end.
             * This wastes a key, but it is necessary because Titan core does not allow
             * the start and end keys in a KeyRange to be the same.
             */
            lb = new StaticArrayBuffer(l.token);
            rb = ByteBufferUtil.decrementAllowUnderflow(new StaticArrayBuffer(r.token));
        } else {
            lb = ByteBufferUtil.decrementAllowUnderflow(new StaticArrayBuffer(l.token));
            rb = ByteBufferUtil.decrementAllowUnderflow(new StaticArrayBuffer(r.token));
        }
        
        final StaticBuffer lbe = ByteBufferUtil.zeroExtendToLength(lb, 4);
        final StaticBuffer rbe = ByteBufferUtil.zeroExtendToLength(rb, 4);
        
        Preconditions.checkArgument(!lbe.equals(rbe));
                
        return new KeyRange(lbe, rbe);
    }
}
