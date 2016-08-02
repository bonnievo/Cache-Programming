// Bonnie Vo
// CSS430
// Spring 2015
// Professor Dimpsey
// Cache.java

import java.lang.System;
import java.util.Vector;

public class Cache {
    // ------------------------------------------------------------------------
    // Private struct of CacheEntry Objects
    // Keeps track of the frame number, reference bit, dirty bit, block data
    private class CacheEntry {
        private int frameNumber;    // disk block number of cached data
                                    // -1 if it doesn't have a valid block
        private boolean referenceBit;   // true whenever block is accessed
        private boolean dirtyBit;       // true whenever this block is written
        private byte[] blockData;       // block data

        // Constructor
        // Initializes all values to be false, -1 and initializes the block
        public CacheEntry(int blockSize) {
            this.frameNumber = -1;
            this.referenceBit = false;
            this.dirtyBit = false;
            this.blockData = new byte[blockSize];
        }

        // Return frame number
        public int getFrameNumber() {
            return this.frameNumber;
        }

        // Return the reference bit
        // True = accessed, false = unaccessed
        public boolean isReference() {
            return this.referenceBit;
        }

        // Return the dirty bit
        // True = written, false = unwritten
        public boolean isDirty() {
            return this.dirtyBit;
        }

        // Return block data
        public byte[] getBlockData() {
            return this.blockData;
        }

        // Set frame number to the param
        public void setFrameNumber(int frameNumber) {
            this.frameNumber = frameNumber;
        }

        // Set reference bit to the param
        public void setReference(boolean reference) {
            this.referenceBit = reference;
        }

        // Set dirty bit to the param
        public void setDirty(boolean dirty) {
            this.dirtyBit = dirty;
        }

        // Set block data to the param
        public void setBlockData(byte[] blockData) {
            this.blockData = blockData;
        }
    }

    // ------------------------------------------------------------------------
    private int INVALID_FRAME = -1;     // Invalid frame number
    private int blockSize;              // Block size
    private int cacheBlocks;            // Cache block size
    private Vector<CacheEntry> cache;   // Vector of CacheEntry objects
    private int victim;                 // Index of victim

    // ------------------------------------------------------------------------
    // Constructor: allocates a cacheBlocks number of cache blocks,
    // each containing blockSize-byte data
    public Cache(int blockSize, int cacheBlocks) {
        this.blockSize = blockSize;
        this.cacheBlocks = cacheBlocks;
        this.cache = new Vector<CacheEntry>();
        this.victim = INVALID_FRAME;

        for (int i = 0; i < cacheBlocks; i++) {
            CacheEntry c = new CacheEntry(blockSize);
            this.cache.add(c);
        }
    }

    // ------------------------------------------------------------------------
    // Block-Read algorithm
    // Reads into the buffer[] array the cache block specified by blockId
    // from the disk cache if it is in cache, otherwise reads the corresponding
    // disk block from the disk device. Upon an error, it should return false,
    // otherwise return true
    public synchronized boolean read(int blockId, byte buffer[]) {
        if (blockId == INVALID_FRAME) {
            SysLib.cerr("ThreadOS - Cache: invalid blockID\n");
            return false;
        }

        CacheEntry readCache = null;

        // go through vector and find corresponding block
        for (int i = 0; i < this.cacheBlocks; i++) {
            if (this.cache.elementAt(i).getFrameNumber() == blockId) {
                readCache = this.cache.elementAt(i);
                byte[] currData = readCache.getBlockData();
                System.arraycopy(currData, 0, buffer, 0, blockSize);
                readCache.setReference(true);
                this.cache.set(i, readCache);
                return true;
            }
        }
        // CASE if readCache is still null - find the next free cache
        victim = findFreeCache();

        // enhanced second-chance algorithm
        if (victim == INVALID_FRAME) {
            victim = findNextVictim();
        }
        writeBack(victim);

        SysLib.rawread(blockId, buffer);

        readCache = this.cache.elementAt(victim);

        byte[] buff = new byte[blockSize];
        System.arraycopy(buffer, 0, buff, 0, blockSize);
        readCache.setBlockData(buff);

        readCache.setFrameNumber(blockId);
        readCache.setReference(true);
        this.cache.set(victim, readCache);

        return true;

    }

    // ------------------------------------------------------------------------
    // findFreeCache
    // find a free block entry in the cache and return the index
    // othewise return -1
    private int findFreeCache() {
        for (int i = 0; i < this.cacheBlocks; i++) {
            if (this.cache.elementAt(i).getFrameNumber() == INVALID_FRAME) {
                return i;
            }
        }
        return INVALID_FRAME;
    }

    // ------------------------------------------------------------------------
    // findNextVictim
    // Cache is full and there is not a free cache block,
    // find a victim using the enhanced second-chance algorithm
    private int findNextVictim() {
        int v = this.victim;
        for (;;) {
            v = (v + 1) % this.cacheBlocks;
            CacheEntry current = this.cache.elementAt(v);
            if (!current.isReference()) {
                return v;
            }
            current.setReference(false);
            this.cache.set(v, current);
        }
    }

    // ------------------------------------------------------------------------
    // writeBack
    // if victim is dirty, first write back its contents to the disk and
    // set dirty bit to false
    private void writeBack(int v) {
        CacheEntry current = this.cache.elementAt(v);
        if (current.getFrameNumber() != INVALID_FRAME && current.isDirty()) {
            byte[] currentCacheBlock = current.getBlockData();
            SysLib.rawwrite(current.getFrameNumber(), currentCacheBlock);
            current.setDirty(false);
            this.cache.set(v, current);
        }
    }


    // ------------------------------------------------------------------------
    // Block-write algorithm
    // Scan the cache for the appropriate block
    // Write the buffer[] array to the cache block specified by blockId
    // Otherwise write the corresponding disk block from the disk device.
    // Upon an error, it should return false, otherwise return true
    public synchronized boolean write(int blockId, byte buffer[]) {
        if (blockId == INVALID_FRAME) {
            SysLib.cerr("ThreadOS - Cache: invalid blockID\n");
            return false;
        }

        CacheEntry writeCache = null;

        // go through vector and find corresponding block
        for (int i = 0; i < this.cache.size(); i++) {
            if (this.cache.elementAt(i).getFrameNumber() == blockId) {
                writeCache = this.cache.elementAt(i);
                writeCache.setReference(true);
                writeCache.setDirty(true);

                byte[] currData = writeCache.getBlockData();

                System.arraycopy(buffer, 0, currData, 0, this.blockSize);
                writeCache.setBlockData(currData);

                cache.set(i, writeCache);
                return true;
            }
        }

        // CASE if writeCache is still null - find the next free cache
        this.victim = findFreeCache();
        if (this.victim == INVALID_FRAME) {
            this.victim = findNextVictim();
        }

        writeBack(this.victim);
        writeCache = this.cache.elementAt(this.victim);

        byte[] buff = new byte[this.blockSize];
        System.arraycopy(buffer, 0, buff, 0, blockSize);
        writeCache.setBlockData(buff);

        writeCache.setFrameNumber(blockId);
        writeCache.setReference(true);
        writeCache.setDirty(true);
        this.cache.set(victim, writeCache);

        return true;

    }

    // ------------------------------------------------------------------------
    // sync
    // write back all dirty blocks to Disk.java
    public synchronized void sync() {
        for (int i = 0; i < cache.size(); i++) {
            this.writeBack(i);
        }
    }
    // ------------------------------------------------------------------------
    // flush
    // write back all dirty blocks to disk.java
    // Should invalidates all cached blocks
    public synchronized void flush() {
        for (int i = 0; i < cache.size(); i++) {
            this.writeBack(i);

            CacheEntry current = this.cache.elementAt(i);
            current.setReference(false);
            current.setFrameNumber(INVALID_FRAME);
            this.cache.set(i, current);
        }
    }
}