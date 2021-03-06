
package handling.login.handler;

import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.MapleClient;
import client.inventory.IItem;
import client.inventory.Item;
import client.inventory.MapleInventory;
import client.inventory.MapleInventoryType;
import handling.channel.ChannelServer;
import handling.login.LoginInformationProvider;
import handling.login.LoginServer;
import handling.login.LoginWorker;
import handling.world.World;
import java.util.Calendar;
import java.util.List;
import server.MapleItemInformationProvider;
import server.ServerProperties;
import server.quest.MapleQuest;
import tools.FileoutputUtil;
import tools.KoreanDateUtil;
import tools.MaplePacketCreator;
import tools.StringUtil;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.LoginPacket;

/**
 *
 * @author zjj
 */
public class CharLoginHandler {

    private static final boolean loginFailCount(final MapleClient c) {
        c.loginAttempt++;
        return c.loginAttempt > 5;
    }

    /**
     *
     * @param c
     */
    public static final void Welcome(final MapleClient c) {
    }

    /**
     *
     * @param slea
     * @param c
     */
    public static final void login(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        final String login = slea.readMapleAsciiString();
        final String pwd = slea.readMapleAsciiString();

        c.setAccountName(login);

        int[] bytes = new int[6];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = slea.readByteAsInt();
        }
        StringBuilder sps = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sps.append(StringUtil.getLeftPaddedStr(Integer.toHexString(bytes[i]).toUpperCase(), '0', 2));
            sps.append("-");
        }
        String macData = sps.toString();
        macData = macData.substring(0, macData.length() - 1);

        c.setMac(macData);

        final boolean ipBan = c.hasBannedIP();
        final boolean macBan = c.isBannedMac(macData);
        final boolean banned = ipBan || macBan;

        int loginok = 0;
        if (Boolean.parseBoolean(ServerProperties.getProperty("ZeroMS.AutoRegister"))) {

            if (AutoRegister.autoRegister && !AutoRegister.getAccountExists(login) && (!banned)) {
                if (pwd.equalsIgnoreCase("disconnect") || pwd.equalsIgnoreCase("fixme")) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "This password is invalid."));
                    c.getSession().write(LoginPacket.getLoginFailed(1)); //Shows no message, used for unstuck the login button
                    return;
                }
                AutoRegister.createAccount(login, pwd, c.getSession().getRemoteAddress().toString(), macData);
                if (AutoRegister.success && AutoRegister.mac) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "账号创建成功,请尝试重新登录!\r\n拒绝一切第三方辅助程序\r\n提倡手动从我做起\r\n\r\n！特别注意：一台电脑只能注册一个账号！"));
                } else if (!AutoRegister.mac) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "账号创建失败，你已经注册过账号\r\n\r\n一个机器码只能注册一个账号"));
                }
                AutoRegister.success = true;
                AutoRegister.mac = true;
                c.getSession().write(LoginPacket.getLoginFailed(1)); //Shows no message, used for unstuck the login button
                return;
            }
        }

        // loginok = c.fblogin(login, pwd, ipBan || macBan);
        loginok = c.login(login, pwd, ipBan || macBan);

        final Calendar tempbannedTill = c.getTempBanCalendar();
        if (loginok == 0 && (ipBan || macBan) && !c.isGm()) {
            loginok = 3;
            if (macBan) {
                // this is only an ipban o.O" - maybe we should refactor this a bit so it's more readable
                //    MapleCharacter.ban(c.getSession().getRemoteAddress().toString().split(":")[0], "Enforcing account ban, account " + login, false, 4, false);
            }
        }
        if (loginok != 0) {
            if (!loginFailCount(c)) {
                c.getSession().write(LoginPacket.getLoginFailed(loginok));
            }
        } else if (tempbannedTill.getTimeInMillis() != 0) {
            if (!loginFailCount(c)) {
                c.getSession().write(LoginPacket.getTempBan(KoreanDateUtil.getTempBanTimestamp(tempbannedTill.getTimeInMillis()), c.getBanReason()));
            }
        } else {
            FileoutputUtil.logToFile("logs/ACPW.txt", "ACC: " + login + " PW: " + pwd + " MAC : " + macData + " IP: " + c.getSession().getRemoteAddress().toString() + "\r\n");
            c.updateMacs();
            c.loginAttempt = 0;
            LoginWorker.registerClient(c);

        }
    }


    /*
     * public static final void login(final SeekableLittleEndianAccessor slea,
     * final MapleClient c) { final String login = slea.readMapleAsciiString();
     * final String pwd = slea.readMapleAsciiString();
     *
     * c.setAccountName(login); final boolean ipBan = c.hasBannedIP(); final
     * boolean macBan = c.hasBannedMac();
     *
     * int loginok = c.login(login, pwd, ipBan || macBan); final Calendar
     * tempbannedTill = c.getTempBanCalendar();
     *
     * if (loginok == 0 && (ipBan || macBan) && !c.isGm()) { loginok = 3; if
     * (macBan) { // this is only an ipban o.O" - maybe we should refactor this
     * a bit so it's more readable
     * MapleCharacter.ban(c.getSession().getRemoteAddress().toString().split(":")[0],
     * "Enforcing account ban, account " + login, false, 4, false); } } if
     * (loginok != 0) { if (!loginFailCount(c)) {
     * c.getSession().write(LoginPacket.getLoginFailed(loginok)); } } else if
     * (tempbannedTill.getTimeInMillis() != 0) { if (!loginFailCount(c)) {
     * c.getSession().write(LoginPacket.getTempBan(KoreanDateUtil.getTempBanTimestamp(tempbannedTill.getTimeInMillis()),
     * c.getBanReason())); } } else { c.loginAttempt = 0;
     * LoginWorker.registerClient(c); } }
     */

    /**
     *
     * @param slea
     * @param c
     */

    public static final void SetGenderRequest(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        byte gender = slea.readByte();
        String username = slea.readMapleAsciiString();
        // String password = slea.readMapleAsciiString();
        if (c.getAccountName().equals(username)) {
            c.setGender(gender);
            //  c.setSecondPassword(password);
            c.updateSecondPassword();
            c.updateGender();
            c.getSession().write(LoginPacket.getGenderChanged(c));
            c.getSession().write(MaplePacketCreator.licenseRequest());
            c.updateLoginState(MapleClient.LOGIN_NOTLOGGEDIN, c.getSessionIPAddress());
        } else {
            c.getSession().close();
        }
    }

    /**
     *
     * @param c
     */
    public static final void ServerListRequest(final MapleClient c) {
        c.getSession().write(LoginPacket.getServerList(0, LoginServer.getServerName(), LoginServer.getLoad()));
        //c.getSession().write(MaplePacketCreator.getServerList(1, "Scania", LoginServer.getInstance().getChannels(), 1200));
        //c.getSession().write(MaplePacketCreator.getServerList(2, "Scania", LoginServer.getInstance().getChannels(), 1200));
        //c.getSession().write(MaplePacketCreator.getServerList(3, "Scania", LoginServer.getInstance().getChannels(), 1200));

        c.getSession().write(LoginPacket.getEndOfServerList());
        c.getSession().write(LoginPacket.enableRecommended());
        c.getSession().write(LoginPacket.sendRecommended(0, LoginServer.getEventMessage()));
    }

    /**
     *
     * @param c
     */
    public static final void ServerStatusRequest(final MapleClient c) {
        // 0 = Select world normally
        // 1 = "Since there are many users, you may encounter some..."
        // 2 = "The concurrent users in this world have reached the max"
        final int numPlayer = LoginServer.getUsersOn();
        final int userLimit = LoginServer.getUserLimit();
        if (numPlayer >= userLimit) {
            c.getSession().write(LoginPacket.getServerStatus(2));
        } else if (numPlayer * 2 >= userLimit) {
            c.getSession().write(LoginPacket.getServerStatus(1));
        } else {
            c.getSession().write(LoginPacket.getServerStatus(0));
        }
    }

    /*  public static final void LicenseRequest(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        if (slea.readByte() == 1) {
            c.getSession().write(MaplePacketCreator.licenseResult());
            c.updateLoginState(0);
        } else {
            c.getSession().close();
        }
    }
     */

    /**
     *
     * @param slea
     * @param c
     */

    public static final void CharlistRequest(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        if (!c.isLoggedIn()) {
            c.getSession().close();
            return;
        }
        // slea.readByte();
        final int server = slea.readByte();
        final int channel = slea.readByte() + 1;
        if (!World.isChannelAvailable(channel) || server != 0) { //TODOO: MULTI WORLDS
            c.getSession().write(LoginPacket.getLoginFailed(10)); //cannot process so many
            return;
        }

        slea.readInt();
        //System.out.println("Client " + c.getSession().getRemoteAddress().toString().split(":")[0] + " is connecting to server " + server + " channel " + channel + "");

        final List<MapleCharacter> chars = c.loadCharacters(server);
        if (chars != null && ChannelServer.getInstance(channel) != null) {
            c.setWorld(server);
            c.setChannel(channel);
            c.getSession().write(LoginPacket.getCharList(c.getSecondPassword() != null, chars, c.getCharacterSlots()));
        } else {
            c.getSession().close();
        }
    }

    /**
     *
     * @param name
     * @param c
     */
    public static final void CheckCharName(final String name, final MapleClient c) {
        c.getSession().write(LoginPacket.charNameResponse(name, !MapleCharacterUtil.canCreateChar(name) || LoginInformationProvider.getInstance().isForbiddenName(name)));
    }

    /**
     *
     * @param slea
     * @param c
     */
    public static final void CreateChar(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        final String name = slea.readMapleAsciiString();
        final int JobType = slea.readInt(); // 1 = Adventurer, 0 = Cygnus, 2 = Aran

        if (JobType == 0) {
            c.getSession().write(MaplePacketCreator.serverNotice(1, "不能创建骑士团"));
            return;
        }
        /*
         * if (JobType == 0 || JobType == 2) {
         * c.getSession().write(MaplePacketCreator.serverNotice(1, "只能创建冒险家喔"));
         * return; }
         */
 /* if (JobType != 1) {
            c.getSession().write(MaplePacketCreator.serverNotice(1, "只能创建冒险家！"));//开放职业
            return;
        }*/
        final short db = 0; //whether dual blade = 1 or adventurer = 0
        final int face = slea.readInt();
        final int hair = slea.readInt();
        final int hairColor = 0;
        final byte skinColor = 0;
        final int top = slea.readInt();
        final int bottom = slea.readInt();
        final int shoes = slea.readInt();
        final int weapon = slea.readInt();

        final byte gender = c.getGender();

        switch (gender) {
            case 0:
                if (face != 20_100 && face != 20_401 && face != 20_402) {
                    return;
                }
                if (hair != 30_030 && hair != 30_027 && hair != 30_000) {
                    return;
                }
                if (top != 1_040_002 && top != 1_040_006 && top != 1_040_010 && top != 1_042_167) {
                    return;
                }
                if (bottom != 1_060_002 && bottom != 1_060_006 && bottom != 1_062_115) {
                    return;
                }
                break;
            case 1:
                if (face != 21_002 && face != 21_700 && face != 21_201) {
                    return;
                }
                if (hair != 31_002 && hair != 31_047 && hair != 31_057) {
                    return;
                }
                if (top != 1_041_002 && top != 1_041_006 && top != 1_041_010 && top != 1_041_011) {
                    return;
                }
                if (bottom != 1_061_002 && bottom != 1_061_008 && bottom != 1_062_115) {
                    return;
                }
                break;
            default:
                return;
        }
        if (shoes != 1_072_001 && shoes != 1_072_005 && shoes != 1_072_037 && shoes != 1_072_038 && shoes != 1_072_383) {
            return;
        }
        if (weapon != 1_302_000 && weapon != 1_322_005 && weapon != 1_312_004 && weapon != 1_442_079) {
            return;
        }

        MapleCharacter newchar = MapleCharacter.getDefault(c, JobType);
        newchar.setWorld((byte) c.getWorld());
        newchar.setFace(face);
        newchar.setHair(hair + hairColor);
        newchar.setGender(gender);
        newchar.setName(name);
        newchar.setSkinColor(skinColor);

        MapleInventory equip = newchar.getInventory(MapleInventoryType.EQUIPPED);
        final MapleItemInformationProvider li = MapleItemInformationProvider.getInstance();

        IItem item = li.getEquipById(top);
        item.setPosition((byte) -5);
        equip.addFromDB(item);

        item = li.getEquipById(bottom);
        item.setPosition((byte) -6);
        equip.addFromDB(item);

        item = li.getEquipById(shoes);
        item.setPosition((byte) -7);
        equip.addFromDB(item);

        item = li.getEquipById(weapon);
        item.setPosition((byte) -11);
        equip.addFromDB(item);

        //blue/red pots
        switch (JobType) {
            case 0: // Cygnus
                newchar.setQuestAdd(MapleQuest.getInstance(20_022), (byte) 1, "1");
                newchar.setQuestAdd(MapleQuest.getInstance(20_010), (byte) 1, null); //>_>_>_> ugh

                newchar.setQuestAdd(MapleQuest.getInstance(20_000), (byte) 1, null); //>_>_>_> ugh
                newchar.setQuestAdd(MapleQuest.getInstance(20_015), (byte) 1, null); //>_>_>_> ugh
                newchar.setQuestAdd(MapleQuest.getInstance(20_020), (byte) 1, null); //>_>_>_> ugh

                newchar.getInventory(MapleInventoryType.ETC).addItem(new Item(4_161_047, (byte) 0, (short) 1, (byte) 0));
                break;
            case 1: // Adventurer
                newchar.getInventory(MapleInventoryType.ETC).addItem(new Item(4_161_001, (byte) 0, (short) 1, (byte) 0));
                break;
            case 2: // Aran
                newchar.getInventory(MapleInventoryType.ETC).addItem(new Item(4_161_048, (byte) 0, (short) 1, (byte) 0));
                break;
            //     case 3: //Evan
            //         newchar.getInventory(MapleInventoryType.ETC).addItem(new Item(4161052, (byte) 0, (short) 1, (byte) 0));
            //        break;            //     case 3: //Evan
            //         newchar.getInventory(MapleInventoryType.ETC).addItem(new Item(4161052, (byte) 0, (short) 1, (byte) 0));
            //        break;
        }

        if (MapleCharacterUtil.canCreateChar(name) && !LoginInformationProvider.getInstance().isForbiddenName(name)) {
            MapleCharacter.saveNewCharToDB(newchar, JobType, JobType == 1 && db == 0);
            c.getSession().write(LoginPacket.addNewCharEntry(newchar, true));
            c.createdChar(newchar.getId());
        } else {
            c.getSession().write(LoginPacket.addNewCharEntry(newchar, false));
        }
    }

    /**
     *
     * @param slea
     * @param c
     */
    public static final void DeleteChar(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        slea.readByte();
        String Secondpw_Client = null;
//        if (slea.readByte() > 0) { // Specific if user have second password or not
        Secondpw_Client = slea.readMapleAsciiString();
//        }
//        slea.readMapleAsciiString();
        final int Character_ID = slea.readInt();

        if (!c.login_Auth(Character_ID)) {
            c.getSession().write(LoginPacket.secondPwError((byte) 0x14));
            return; // Attempting to delete other character
        }
        byte state = 0;

        if (c.getSecondPassword() != null) { // On the server, there's a second password
            if (Secondpw_Client == null) { // Client's hacking
                c.getSession().close();
                return;
            } else if (!c.CheckSecondPassword(Secondpw_Client)) { // Wrong Password
                //state = 12;
                state = 16;
            }
        }
        // TODO, implement 13 digit Asiasoft passport too.

        if (state == 0) {
            state = (byte) c.deleteCharacter(Character_ID);
        }
        c.getSession().write(LoginPacket.deleteCharResponse(Character_ID, state));
    }

    /**
     *
     * @param slea
     * @param c
     */
    public static void Character_WithoutSecondPassword(final SeekableLittleEndianAccessor slea, final MapleClient c) {
//        slea.skip(1);
        /*
         * if (c.getLoginState() != 2) { return; }
         */
        final int charId = slea.readInt();
        if ((!c.isLoggedIn()) || (loginFailCount(c)) || (!c.login_Auth(charId))) {
            c.getSession().write(MaplePacketCreator.enableActions());
            return;
        }
        if ((ChannelServer.getInstance(c.getChannel()) == null) || (c.getWorld() != 0)) {
            c.getSession().close();
            return;
        }
        if (c.getIdleTask() != null) {
            c.getIdleTask().cancel(true);
        }
        String ip = c.getSessionIPAddress();
        LoginServer.putLoginAuth(charId, ip.substring(ip.indexOf('/') + 1, ip.length()), c.getTempIP(), c.getChannel());
        // c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, ip);
        /*
         * if (c.getLoginState() == 2) { c.updateLoginState(2, ip);
         * System.out.println("输出登录2"); } else {
         */
        c.getSession().write(MaplePacketCreator.getServerIP(Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1]), charId));

        /*
         * final String currentpw = c.getSecondPassword(); if (slea.available()
         * != 0) { if (currentpw != null) { // Hack c.getSession().close();
         * return; } final String setpassword = slea.readMapleAsciiString();
         *
         * if (setpassword.length() >= 4 && setpassword.length() <= 16) {
         * c.setSecondPassword(setpassword); c.updateSecondPassword();
         *
         * if (!c.login_Auth(charId)) { c.getSession().close(); return; } } else
         * { c.getSession().write(LoginPacket.secondPwError((byte) 0x14));
         * return; } } else if (loginFailCount(c) || currentpw != null ||
         * !c.login_Auth(charId)) { c.getSession().close(); return; }
         */
        //这句是我屏蔽的
        //   String ip = c.getSessionIPAddress();
        //   LoginServer.putLoginAuth(charId, ip.substring(ip.indexOf('/') + 1, ip.length()), c.getTempIP(), c.getChannel());
        //   c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, c.getSessionIPAddress());        
        //   System.out.println("··········A"+charId);
        //    System.out.println("··········C"+c.getSessionIPAddress());
        //    System.out.println("··········B"+ChannelServer.getInstance(c.getChannel()).getIP());
        //    c.getSession().write(MaplePacketCreator.getServerIP(Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1]), charId));
        //  c.getSession().write(MaplePacketCreator.getServerIP(0, charId));
    }

    /**
     *
     * @param slea
     * @param c
     */
    public static final void Character_WithSecondPassword(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        final String password = slea.readMapleAsciiString();
        final int charId = slea.readInt();

        if (loginFailCount(c) || c.getSecondPassword() == null || !c.login_Auth(charId)) { // This should not happen unless player is hacking
            c.getSession().close();
            return;
        }
        if (c.CheckSecondPassword(password)) {
            c.updateMacs(slea.readMapleAsciiString());
            if (c.getIdleTask() != null) {
                c.getIdleTask().cancel(true);
            }
            c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, c.getSessionIPAddress());
            c.getSession().write(MaplePacketCreator.getServerIP(Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1]), charId));
        } else {
            c.getSession().write(LoginPacket.secondPwError((byte) 0x14));
        }
    }
}
