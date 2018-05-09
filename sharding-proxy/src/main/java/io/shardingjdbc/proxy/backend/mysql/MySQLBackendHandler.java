/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingjdbc.proxy.backend.mysql;

import com.google.common.primitives.Bytes;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.shardingjdbc.proxy.backend.common.CommandResponsePacketsHandler;
import io.shardingjdbc.proxy.transport.mysql.constant.CapabilityFlag;
import io.shardingjdbc.proxy.transport.mysql.constant.ServerInfo;
import io.shardingjdbc.proxy.transport.mysql.packet.MySQLPacketPayload;
import io.shardingjdbc.proxy.transport.mysql.packet.generic.EofPacket;
import io.shardingjdbc.proxy.transport.mysql.packet.generic.ErrPacket;
import io.shardingjdbc.proxy.transport.mysql.packet.generic.OKPacket;
import io.shardingjdbc.proxy.transport.mysql.packet.handshake.HandshakeResponse41Packet;
import io.shardingjdbc.proxy.util.MySQLResultCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Backend handler.
 *
 * @author wangkai
 */
@Slf4j
@RequiredArgsConstructor
public class MySQLBackendHandler extends CommandResponsePacketsHandler {
    
    private boolean authorized;
    private final String ip;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    
    @Override
    public void channelRead(final ChannelHandlerContext context, final Object message) {
        MySQLPacketPayload mysqlPacketPayload = new MySQLPacketPayload((ByteBuf) message);
        //int packetSize = mysqlPacketPayload.readInt3();
        int sequenceId = mysqlPacketPayload.readInt1();
        int header = mysqlPacketPayload.readInt1() & 0xFF;
        if (OKPacket.HEADER == header || ErrPacket.HEADER == header || EofPacket.HEADER == header) {
            genericResponsePacket(context, header, mysqlPacketPayload);
        } else if (!authorized) {
            auth(context, sequenceId, header, mysqlPacketPayload);
        } else {
            executeCommandResponsePackets(context, header, mysqlPacketPayload);
        }
    }
    
    @Override
    protected void auth(ChannelHandlerContext context, int sequenceId, int header, MySQLPacketPayload mysqlPacketPayload) {
        int protocolVersion = header;
        String serverVersion = mysqlPacketPayload.readStringNul();
        int connectionId = mysqlPacketPayload.readInt4();
        MySQLResultCache.getInstance().putConnectionMap(context.channel().id().asShortText(), connectionId);
        byte[] authPluginDataPart1 = mysqlPacketPayload.readStringNul().getBytes();
        int capabilityFlagsLower = mysqlPacketPayload.readInt2();
        int charset = mysqlPacketPayload.readInt1();
        int statusFlag = mysqlPacketPayload.readInt2();
        int capabilityFlagsUpper = mysqlPacketPayload.readInt2();
        int authPluginDataLength = mysqlPacketPayload.readInt1();
        mysqlPacketPayload.skipReserved(10);
        byte[] authPluginDataPart2 = mysqlPacketPayload.readStringNul().getBytes();
        byte[] authPluginData = Bytes.concat(authPluginDataPart1, authPluginDataPart2);
        //byte[] authResponse = byteXOR(SHA1(password.getBytes()), SHA1(Bytes.concat(authPluginData, SHA1(SHA1(password.getBytes())))));
        byte[] authResponse = securePasswordAuthentication(password.getBytes(),authPluginData);
        //TODO maxSizePactet should be set.
        HandshakeResponse41Packet handshakeResponse41Packet = new HandshakeResponse41Packet(sequenceId + 1, CapabilityFlag.calculateHandshakeCapabilityFlagsLower(), 16777215, ServerInfo.CHARSET, username, authResponse, database);
        context.writeAndFlush(handshakeResponse41Packet);
    }
    
    @Override
    protected void genericResponsePacket(ChannelHandlerContext context, int header, MySQLPacketPayload mysqlPacketPayload) {
        switch (header) {
            case OKPacket.HEADER:
                if (!authorized) {
                    authorized = true;
                }
                long affectedRows = mysqlPacketPayload.readIntLenenc();
                long lastInsertId = mysqlPacketPayload.readIntLenenc();
                int statusFlags = mysqlPacketPayload.readInt2();
                int warnings = mysqlPacketPayload.readInt2();
                String info = mysqlPacketPayload.readStringEOF();
                log.debug("OKPacket[affectedRows={},lastInsertId={},statusFlags={},warnings={},info={}]", affectedRows, lastInsertId, statusFlags, warnings, info);
                break;
            case ErrPacket.HEADER:
                int errorCode = mysqlPacketPayload.readInt2();
                String sqlStateMarker = mysqlPacketPayload.readStringFix(1);
                String sqlState = mysqlPacketPayload.readStringFix(5);
                String errorMessage = mysqlPacketPayload.readStringEOF();
                log.debug("ErrPacket[errorCode={},sqlStateMarker={},sqlState={},errorMessage={}]", errorCode, sqlStateMarker, sqlState, errorMessage);
                break;
            case EofPacket.HEADER:
                warnings = mysqlPacketPayload.readInt2();
                statusFlags = mysqlPacketPayload.readInt2();
                log.debug("EofPacket[warnings={},statusFlags={}]", warnings, statusFlags);
                break;
        }
    }
    
    //TODO
    @Override
    protected void executeCommandResponsePackets(ChannelHandlerContext context, int header, MySQLPacketPayload mysqlPacketPayload) {
        int connectionId = MySQLResultCache.getInstance().getonnectionMap(context.channel().id().asShortText());
        MySQLResultCache.getInstance().get(connectionId).setResponse(null);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //TODO delete connection map.
        super.channelInactive(ctx);
    }
    
    private final byte[] securePasswordAuthentication(byte[] password, byte[] authPluginData){
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] part1 = sha1.digest(password);
            sha1.reset();
            byte[] part2 = sha1.digest(part1);
            sha1.reset();
            sha1.update(authPluginData);
            byte[] authResponse = sha1.digest(part2);
            for (int i = 0; i < authResponse.length; i++) {
                authResponse[i] = (byte) (authResponse[i] ^ part1[i]);
            }
            return authResponse;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }
}
