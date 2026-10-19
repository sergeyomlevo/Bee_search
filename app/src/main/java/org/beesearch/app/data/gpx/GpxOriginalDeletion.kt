package org.beesearch.app.data.gpx

import android.system.Os
import java.io.File
import java.util.UUID

/** Deletion uses a persisted inode witness, independent of aborted-import compensation. */
internal class GpxOriginalDeletion(private val files:GpxOriginalFiles,private val checkpoint:(String)->Unit) {
    private fun dir(id:UUID)=File(files.originals,id.toString())
    private fun read(file:File):String=files.open(file).use { input ->
        val bytes=ByteArray(1025);var size=0
        while(size<bytes.size) { val n=input.read(bytes,size,bytes.size-size);if(n<0)break;if(n>0)size+=n }
        require(size<bytes.size);bytes.copyOf(size).toString(Charsets.US_ASCII)
    }
    private fun checkProof(request:UUID,id:UUID,identity:GpxOriginalIdentity,proof:String):List<String> {
        val fields=proof.split('\n')
        require(fields.size==10 && fields[0]=="BEESEARCH-GPX-PUBLICATION-1" && fields[1]==request.toString() && fields[2]==id.toString())
        require(fields[3]==identity.length.toString() && fields[4].equals(identity.sha256,true))
        for(i in 5..8) fields[i].toLong()
        return fields
    }
    private fun verify(request:UUID,id:UUID,identity:GpxOriginalIdentity,proof:String) {
        val fields=checkProof(request,id,identity,proof);val directory=dir(id)
        files.requireDirectory(directory)
        val info=Os.lstat(directory.path)
        require(info.st_dev==fields[5].toLong() && info.st_ino==fields[6].toLong())
        val entries=directory.list()?.toSet() ?: error("DIRECTORY_UNAVAILABLE")
        require(setOf("owner","verified","original.gpx").containsAll(entries))
        for(name in listOf("owner","verified")) { val f=File(directory,name);if(files.stat(f)!=null)require(read(f)==proof) }
        val payload=File(directory,"original.gpx")
        if(files.stat(payload)!=null) files.open(payload).use { input ->
            val stat=Os.fstat(input.fd);require(stat.st_dev==fields[7].toLong() && stat.st_ino==fields[8].toLong())
        }
    }
    fun capture(request:UUID,id:UUID,identity:GpxOriginalIdentity):String? {
        files.prepare();if(files.stat(dir(id))==null)return null
        val proof=read(File(dir(id),"owner"));verify(request,id,identity,proof);return proof
    }
    fun cleanup(request:UUID,id:UUID,identity:GpxOriginalIdentity,proof:String?) {
        files.prepare();val directory=dir(id)
        if(files.stat(directory)==null) { files.syncDirectory(files.originals);return }
        requireNotNull(proof);verify(request,id,identity,proof)
        for(name in listOf("original.gpx","verified","owner")) {
            val file=File(directory,name)
            if(files.stat(file)!=null) { files.unlink(file);files.syncDirectory(directory) }
            checkpoint("UNLINK_$name")
        }
        Os.remove(directory.path);checkpoint("DIRECTORY_REMOVED")
        files.syncDirectory(files.originals);checkpoint("ORIGINALS_SYNCED")
    }
}
