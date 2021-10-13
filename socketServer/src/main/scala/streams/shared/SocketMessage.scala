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

  // ascii representation for "<TT>"
  private val NET_HDR_ID           = 0x3c54543e
//  private val NET_HDR_ID           = 0x3e54543c
  private[streams] val NET_HDR_LEN = 2 * 4

  // C code uses fixed size messages
  private val CMD_LEN: Short = 256
  private val MSG_SIZE: Short = (MsgHdr.encodedSize + CMD_LEN).toShort

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
    try {
      val buffer = bs.toByteBuffer.order(ByteOrder.LITTLE_ENDIAN)

      // from struct msg_hdr_dcl
      buffer.getInt()
      buffer.getInt()

      // MsgHdr
      val msgId  = MessageId(buffer.getShort() & 0x0ffff)
      val srcId  = SourceId(buffer.getShort() & 0x0ffff)
      val msgLen = buffer.getShort() & 0x0ffff
      val seqNo  = buffer.getShort() & 0x0ffff

      // Message/Command contents
      val msgHdr = MsgHdr(msgId, srcId, msgLen, seqNo)

      val bytes = Array.fill(msgLen - MsgHdr.encodedSize)(0.toByte)
      buffer.get(bytes)
      // Remove any trailing null chars
      val s = new String(bytes, StandardCharsets.UTF_8).split('\u0000').head
      SocketMessage(msgHdr, s)
    } catch {
      case ex: Exception =>
        ex.printStackTrace()
        throw ex
    }
  }
}

case class SocketMessage(hdr: MsgHdr, cmd: String) {

  /**
   * Encodes the command for sending (see parse)
   */
  def toByteString: ByteString = {
    val buffer1 = ByteBuffer.allocateDirect(NET_HDR_LEN + MSG_SIZE).order(ByteOrder.BIG_ENDIAN)
    // from struct msg_hdr_dcl
    buffer1.putInt(NET_HDR_ID)
//    buffer.putInt(hdr.msgLen)
    buffer1.putInt(MSG_SIZE)

    val buffer = buffer1.order(ByteOrder.LITTLE_ENDIAN)
    // from MsgHdr
    buffer.putShort(hdr.msgId.id.toShort)
    buffer.putShort(hdr.srcId.id.toShort)
//    buffer.putShort(hdr.msgLen.toShort)
    buffer.putShort(MSG_SIZE)
    buffer.putShort(hdr.seqNo.toShort)

    // Contents of message/command
//    buffer.put(cmd.getBytes(StandardCharsets.UTF_8))
    val nullBytes = Array.fill(CMD_LEN-cmd.length)(0.toByte)
    val ar = cmd.getBytes(StandardCharsets.UTF_8) ++ nullBytes
    buffer.put(ar)

    buffer.flip()
    ByteString.fromByteBuffer(buffer)
  }
}
