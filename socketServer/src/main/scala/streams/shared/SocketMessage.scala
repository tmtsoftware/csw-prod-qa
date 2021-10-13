package streams.shared
import akka.util.ByteString
import streams.shared.SocketMessage.*

import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets

/**
 * Represents commands to the server as well as responses from the server.
 */
object SocketMessage {
  // system wide message ID's
  case class MessageId(id: Int)
  val CMD_TYPE: MessageId  = MessageId(1 << 8)
  val RSP_TYPE: MessageId  = MessageId(2 << 8)
  val LOG_TYPE: MessageId  = MessageId(3 << 8)
  val DATA_TYPE: MessageId = MessageId(4 << 8)

  // sender application ID (TODO: Define ids)
  case class SourceId(id: Int)

  object MsgHdr {
    // Size in bytes when encoded
    val encodedSize: Int = 4 * 2
  }

  /**
   *
   * @param msgId message type
   * @param srcId sender application id
   * @param msgLen message length including header(bytes
   * @param seqNo sequence number
   */
  case class MsgHdr(msgId: MessageId, srcId: SourceId, msgLen: Int, seqNo: Int)

  /**
   * Parses the command from the given ByteString
   */
  def parse(bs: ByteString): SocketMessage = {
    val buffer = bs.toByteBuffer.order(ByteOrder.LITTLE_ENDIAN)
    val msgId  = MessageId(buffer.getShort() & 0x0ffff)
    val srcId  = SourceId(buffer.getShort() & 0x0ffff)
    val msgLen = buffer.getShort() & 0x0ffff
    val seqNo  = buffer.getShort() & 0x0ffff
    val msgHdr = MsgHdr(msgId, srcId, msgLen, seqNo)
    val bytes  = Array.fill(msgLen - MsgHdr.encodedSize)(0.toByte)
    buffer.get(bytes)
    SocketMessage(msgHdr, new String(bytes, StandardCharsets.UTF_8))
  }
}

case class SocketMessage(hdr: MsgHdr, cmd: String) {

  /**
   * Encodes the command for sending (see parse)
   */
  def toByteString: ByteString = {
    val buffer = ByteBuffer.allocateDirect(MsgHdr.encodedSize + cmd.length).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putShort(hdr.msgId.id.toShort)
    buffer.putShort(hdr.srcId.id.toShort)
    buffer.putShort(hdr.msgLen.toShort)
    buffer.putShort(hdr.seqNo.toShort)
    buffer.put(cmd.getBytes(StandardCharsets.UTF_8))
    buffer.flip()
    ByteString.fromByteBuffer(buffer)
  }
}
