package l1j.server.server.model.item.function;

import static l1j.server.server.model.item.function.BugRaceControlWandItems.use_bug_down_wand;
import static l1j.server.server.model.item.function.CashBuffTransactions.강화버프;
import static l1j.server.server.model.item.function.ChargedRewardBoxItems.UseDurableChargedBox;
import static l1j.server.server.model.item.function.CombatAbilityItems.쉐도우;
import static l1j.server.server.model.item.function.CombatAbilityItems.이터니티;
import static l1j.server.server.model.item.function.CombatAbilityItems.전투지휘봉;
import static l1j.server.server.model.item.function.CombatSkillItemEffects.군주광역스턴;
import static l1j.server.server.model.item.function.CombatSkillItemEffects.비전텔레포트;
import static l1j.server.server.model.item.function.DogFightEnhancementItems.투견조작;
import static l1j.server.server.model.item.function.EquipmentItemActions.handleArmorUse;
import static l1j.server.server.model.item.function.EquipmentItemActions.handleWeaponUse;
import static l1j.server.server.model.item.function.NpcRemovalWandItems.useFieldObjectRemovalWand;
import static l1j.server.server.model.item.function.NpcWandAndSpawnItems.useNpcSpownWand;
import static l1j.server.server.model.item.function.TopRankerBlessingItems.정상의가호;
import static l1j.server.server.model.item.function.TopRankerBlessingItems.정상의가호2;
import static l1j.server.server.model.item.function.TowerTeleportAmulets.useToiTeleportAmulet;
import static l1j.server.server.model.item.function.TransformationItems.usePolyPotion;
import static l1j.server.server.model.item.function.WandInspectionItems.serchdroplist2;
import static l1j.server.server.model.item.function.WandInspectionItems.useFieldObjectRemovalWand1;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.item.classchange.ClassChange;
import l1j.server.server.model.item.collection.CollectionHandler;
import l1j.server.server.model.item.collection.loader.CollectionLoadManager;
import l1j.server.server.model.item.enchant.ItemEnchanterLoader;
import l1j.server.server.templates.BookmarkSlot;

/** Explicit-ID dispatch. The default retains its original ordered range/use-type rules. */
final class ItemActionDispatcher {
    private ItemActionDispatcher() {}

    /** Shared equipment path for the context-free service fast path. */
    static void useEquipment(L1PcInstance pc, L1ItemInstance item, int type2) {
        if (type2 == 1) handleWeaponUse(pc, item);
        else if (type2 == 2) handleArmorUse(pc, item);
    }

    static ItemActionResult use(ItemActionContext use) {
        final int type2 = use.item().getItem().getType2();
        if (type2 != 0) {
            useEquipment(use.player(), use.item(), type2);
            return ItemActionResult.COMPLETE;
        }
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        int itemId = use.itemId();
        int spellTargetId = use.spellTargetId();
        int spellTargetX = use.spellX();
        int spellTargetY = use.spellY();
        short bookmarkMapId = use.bookmarkMapId();
        int bookmarkX = use.bookmarkX();
        int bookmarkY = use.bookmarkY();
        int teleportBookSlot = use.teleportBookSlot();
        int storedItemObjectId = use.storedItemObjectId();
        return switch(itemId) {
            case 700085, 700086 -> AnnouncementItemUse.handleRequestAnnouncement(use);
            case 42656, 90736, 90735, 3000245, 3000244, 3000242, 90737, 6012,
                    6016 -> { UseDurableChargedBox(pc, item); yield ItemActionResult.STOP; }
            case 41922, 41923, 41924, 41925 -> CombatBuffConsumables.handleCastGmBuff(use);
            case 50020, 50021 -> { SealRemovalScrolls.use(pc, item, targetItem, itemId); yield ItemActionResult.STOP; }
            case L1ItemId.DRAGON_PEARL, L1ItemId.DRAGON_PEARL1 -> CombatBuffConsumables.handleDragonPearl(use);
            case 3000224 -> GemConversionItems.handleConvertAinGems(use);
            case 410139, 6017, 410138, 410064, 1000002, 1000003, 1000004, 1000007,
                    3000231, 7241, 3000457, 60255 -> BlessingConsumables.handleAinGem(use);
            case 410032, 410033, 410034, 410035, 410036, 410037, 410038, 779911,
                    779922, 779933, 3000159 -> BlessingConsumables.handleMaanOrRaid(use);
            case 410010, 410011, 410012, 4100039, 4100041, 4100042, 30063 -> FoodAndPotionConsumables.handleCashScroll(use);
            case 7007, 40010, 40011, 40012, 4100021, 40019, 40020, 40021,
                    40022, 40023, 40024, 40026, 40027, 40028, 40043, 40058,
                    40071, 40506, 40930, 41141, 41337, 60029, 60030, 140010,
                    140011, 140012, 140506, 240010, 41403, 410000, 410003, 30062,
                    30056, 42658, 40029, 4100152, 4100153, 4100154, 40858, 708,
                    3000456, 30105, 210094, 40030, 40013, 7006, 40018, 40039,
                    40040, 41338, 41261, 41262, 41268, 41269, 41271, 41272,
                    41273, 41342, 30067, 140013, 140018, 30158, 875640293, 40014,
                    140014, 41415, 30073, 40068, 140068, 210110, 30076, 712,
                    40031, 30075, 713, 210036, 30077, 40733, 210115, 40032,
                    40041, 41344, 30083, 40015, 140015, 40736, 41142, 210114,
                    30089, 40016, 140016, 210113, 40025 -> FoodAndPotionConsumables.handlePotion(use);
            case 4100133 -> TransformationListItems.handleShowJinDeathForms(use);
            case 30060 -> TransformationListItems.handleShowPixieForms(use);
            case 3000421 -> TransformationListItems.handleShowTamForms(use);
            case 600198, 600199, 600200, 600201, 600202, 600203, 600204, 600205,
                    600206, 600207, 600208, 600209, 600210, 600211, 600212, 600213,
                    600214, 600215, 600216, 600217, 600218 -> { 강화버프(pc, itemId, item); yield ItemActionResult.STOP; }
            case 600223 -> PcRoomConsumables.handlePcRoomBuff(use);
            case 3000237 -> PcRoomConsumables.handlePcRoomGift(use);
            case 600225 -> PcRoomConsumables.handleGrowthPcRoomBuff(use);
            case 3000201 -> PcRoomConsumables.handleSupportBuff(use);
            case CollectionLoadManager.CTSYSTEM_STORE_ID -> { CollectionHandler.store(pc, item, storedItemObjectId,
                    () -> ItemActionCompletion.finishCommittedConsumable(pc, item)); yield ItemActionResult.STOP; }
            case 600226, 3000235, 600227 -> CharacterServiceItems.handleStoreClassChange(use);
            case 40096 -> TransformationScrollItems.handlePolyScroll(use);
            case 3000467 -> TransformationScrollItems.handleEventPolyScroll(use);
            case 140088 -> TransformationScrollItems.handleBlessedPolyScroll(use);
            case 42655 -> TransformationScrollItems.handleTrainingPolyScroll(use);
            case 4100290 -> { usePolyPotion(pc, itemId); yield ItemActionResult.COMPLETE; }
            case 4100284, 4100285, 4100286, 4100287, 4100288, 4100289, 4200100, 4200400,
                    5922901, 41143, 41144, 41145, 30057, 30058, 30059, 8000,
                    8001, 8002 -> TransformationScrollItems.handleFixedPolyPotion(use);
            case 210097, 210098, 210099, 210100, 210101, 210102, 210103, 210116,
                    210117 -> TransformationScrollItems.handleLevelPolyScroll(use);
            case 40317, 30087 -> QuestRepairScrollUse.handleCombineMapFragments(use);
            case 210073, 210077, 500205, 500207 -> QuestTreasureFragmentCombine.handleCombineTreasureFragments(use);
            case 40126, 40098 -> IdentificationAndScrollItems.handleIdentifyItem(use);
            case 41048, 41049, 41050, 41051, 41052, 41053, 41054, 41055,
                    41056, 41057 -> QuestVoucherExchange.handleVoucher(use);
            case 40931, 40932, 40933, 40934, 40935, 40936, 40937, 40938,
                    40939, 40940, 40941, 40942 -> QuestEarringCollection.handleBagCollection(use);
            case 40943, 40944, 40945, 40946, 40947, 40948, 40949, 40950,
                    40951, 40952, 40953, 40954, 40955, 40956, 40957, 40958 -> QuestRingCollection.handleMixedBagCollection(use);
            case 41029 -> QuestSummoningFragmentCombine.handleSummonFragments(use);
            case 40090, 40091, 40092, 40093, 40094 -> IdentificationAndScrollItems.handleWriteBlankScroll(use);
            case 40314, 40316 -> PetAndSummonItems.handlePlayRaceFlute(use);
            case 40315 -> PetAndSummonItems.handleSpecialFlute(use);
            case 40089, 140089, 30074 -> ResurrectionConsumables.handleTeleportCompanions(use);
            case 40095, 40079, 40521 -> { DominationTowerReturnHandler.getInstance().handleReturn(pc, item); yield ItemActionResult.STOP; }
            case 41159, 41921 -> SpecialMapEscapeItems.handleReturnFromSpecialMap(use);
            case 40117 -> { yield ItemActionResult.COMPLETE; }
            case 40099, 40100, 40086, 40863, 140100, 150100 -> { TeleportUseService.clickItem(pc, itemId, bookmarkX, bookmarkY, bookmarkMapId, item); yield ItemActionResult.STOP; }
            case 830001, 830002, 830003, 830004, 830005, 830006, 830007, 830008,
                    830009, 830010, 830011 -> { OmanTowerTeleportHandler.getInstance().clickItem(pc, itemId, item); yield ItemActionResult.COMPLETE; }
            case 830012, 830013, 830014, 830015, 830016, 830017, 830018, 830019,
                    830020, 830021, 830022, 830023, 830024, 830025, 830026, 830027,
                    830028, 830029, 830030, 830031 -> { OmanAmuletHandler.getInstance().clickItem(pc, itemId); yield ItemActionResult.COMPLETE; }
            case 830042, 830043, 830044, 830045, 830046, 830047, 830048, 830049,
                    830050, 830051, 830052, 830053, 830054, 830055, 830056, 830057,
                    830058, 830059, 830060, 830061 -> { OmanRandomAmulet.clickItem(pc, itemId, item); yield ItemActionResult.COMPLETE; }
            case 40901, 40902, 40903, 40904, 40905, 40906, 40907, 40908 -> EngagementRingItemUse.handleEngagementRing(use);
            case 40555 -> QuestTravelItems.handleKnightQuest(use);
            case 40572 -> QuestTravelItems.handleQuestTeleport(use);
            case 700022 -> QuestProgressItems.handleMarkReward(use);
            case 40420, 40421, 40422, 40423 -> QuestAncientBookCombine.handleCombineAncientBooks(use);
            case 3000455, 30104 -> CombatBuffConsumables.handleComaCoin(use);
            case 4100080, 7020, 7021 -> { ItemRewardPackages.clickItem(pc, itemId, item); yield ItemActionResult.STOP; }
            case 60036, 60037, 60038, 60039, 60040 -> { ItemRewardPackages.clickItem(pc, itemId, item); yield ItemActionResult.STOP; }
            case 60041, 60042, 60043, 60044, 60045 -> { ItemRewardPackages.clickItem(pc, itemId, item); yield ItemActionResult.STOP; }
            case 68078, 68079, 68080, 68081, 68082 -> { ItemRewardPackages.clickItem(pc, itemId, item); yield ItemActionResult.STOP; }
            case 68083, 68084, 68085, 68086, 68087 -> { ItemRewardPackages.clickItem(pc, itemId, item); yield ItemActionResult.STOP; }
            case 68088, 68089, 68090, 68091, 68092 -> { ItemRewardPackages.clickItem(pc, itemId, item); yield ItemActionResult.STOP; }
            case 30124 -> SiegeSupplyItems.showCatapultSupplyNotice(use);
            case 500218 -> FoodAndPotionConsumables.handleKarmaPotion(use);
            case 40008, 140008, 40410 -> TargetPolymorphWands.handleResurrectionScroll(use);
            case 40289, 40290, 40291, 40292, 40293, 40294, 40295, 40296,
                    40297 -> { useToiTeleportAmulet(pc, itemId, item); yield ItemActionResult.COMPLETE; }
            case 40280, 40281, 40282, 40283, 40284, 40285, 40286, 40287,
                    40288, 830032, 830033, 830034, 830035, 830036, 830037, 830038,
                    830039, 830040, 830041 -> { LegacyEquipmentExchangeReceipt.exchangeLegacyItem(pc, item); yield ItemActionResult.STOP; }
            case 40056, 40057, 40059, 40060, 40061, 40062, 40063, 40064,
                    40065, 40069, 40072, 40073, 41297, 49092, 41266, 41267,
                    41274, 41275, 41276, 41252, 49040, 49041, 49042, 49043,
                    49044, 49045, 49046, 49047, 140061, 140062, 140065, 140069,
                    140072, 410056, 210039, 30085 -> FoodAndPotionConsumables.handleFood(use);
            case 77799 -> { ClassPackageRewardReceipt.useClassPackage(pc, item); yield ItemActionResult.STOP; }
            case 408991 -> NameChangeRequestItemUse.handleRequestNameChange(use);
            case 41146, 41209, 41210, 41211, 41212, 41213, 41214, 41215, 41216, 41222, 41223, 41224,
                    41225, 41226, 41227, 41228, 41229, 41230, 41231, 41233, 41234, 41235, 41239, 41240,
                    41060, 41061, 41062, 41063, 41064, 41065, 41356, 40663, 40630, 41340, 41317, 41318,
                    41329, 41346, 41347, 41348, 41007, 41009, 41019, 41020, 41021, 41022, 41023, 41024,
                    41025, 41026, 210087, 210093, 410106, 410101, 410103, 410105, 410098, 410099, 410100, 410097,
                    410102, 410104, 410107 -> { QuestDialogueItems.send(pc, itemId); yield ItemActionResult.COMPLETE; }
            case 40701 -> QuestTravelItems.handleLukeinRecord(use);
            case 42501 -> WorldDestinationItems.handlePointTeleport(use);
            case 41293, 41294, 41305, 41306, 4100293, 4200293, 600229, 87058,
                    87059, 9991 -> FishingUtilityItems.handleStartFishing(use);
            case 7024 -> ClanMarkDisplayItems.handleFishingRod(use);
            case 200000 -> CharacterServiceItems.handleResetStats(use);
            case 3000049 -> ExperienceRecoveryConsumables.handleRecoveryVoucherNotice(use);
            case 3000124 -> PetAndSummonItems.handleSummonMonster(use);
            case 210118 -> ProtectedClanJoinItemUse.handleJoinProtectedClan(use);
            case 7643 -> BlessingConsumables.handleMembershipBuff(use);
            case 400253, 875640445 -> { AdenaCurrencyConversionReceipt.convertAdena(pc, item); yield ItemActionResult.STOP; }
            case 875640295 -> TransformationCollectionCards.handleUnlockCollection(use);
            case 875640296, 875640297, 875640298, 875640299, 875640300, 875640301, 875640302, 875640303,
                    875640304, 875640305, 875640380 -> TransformationCollectionCards.handleSelectCollection(use);
            case 875640306, 875640307, 875640308, 875640309, 875640310, 875640311, 875640312, 875640313,
                    875640314 -> TransformationCollectionCards.handleSelectCollectionGroup2(use);
            case 875640365, 875640366, 875640367, 875640368, 875640369, 875640370, 875640371, 875640372,
                    875640373, 875640374, 875640375, 875640376, 875640377, 875640378 -> TransformationCollectionCards.handleSelectCollectionGroup3(use);
            case 875640315, 875640316, 875640317, 875640318, 875640319, 875640320, 875640321, 875640322,
                    875640323, 875640324, 875640325, 875640326, 875640327, 875640328, 875640329, 875640330 -> TransformationCollectionCards.handleSelectCollectionGroup4(use);
            case 875640345, 875640346, 875640347, 875640348, 875640349, 875640350, 875640351, 875640352,
                    875640353, 875640354, 875640355, 875640356, 875640357, 875640358, 875640359, 875640360,
                    875640361, 875640362, 875640363, 875640364 -> TransformationCollectionCards.handleSelectCollectionGroup5(use);
            case 875640331, 875640332, 875640333, 875640334, 875640335, 875640336, 875640337, 875640338,
                    875640339, 875640340, 875640341, 875640446, 875640447, 875640448 -> TransformationCollectionCards.handleSelectCollectionGroup6(use);
            case 700081 -> { 군주광역스턴(pc); yield ItemActionResult.COMPLETE; }
            case 600228 -> FishingUtilityItems.handleUpgradeFishingReel(use);
            case 123123 -> { AdminItemPackages.open(pc, item); yield ItemActionResult.STOP; }
            case 3000063 -> { LegacyEquipmentExchangeReceipt.exchangeLegacyItem(pc, item); yield ItemActionResult.STOP; }
            case 110002 -> DollPotentialEnhancementScrolls.handleDollPotential(use);
            case 7010, 7011, 8010, 8011, 5922910, 5922911, 622910, 633910, 885544 -> { SpecialEnchantmentItems.use(pc, item, targetItem, itemId); yield ItemActionResult.STOP; }
            case 65648, 40879 -> { DurableBuffScrollItems.use(pc, item, itemId); yield ItemActionResult.STOP; }
            case 40870 -> CombatBuffConsumables.handleBlessWeaponScroll(use);
            case 3000210 -> ExperienceRecoveryConsumables.handleOpenExperienceBox(use);
            case 400254, 500254, 600254 -> { SpecialConsumableRouter.use(pc, item); yield ItemActionResult.STOP; }
            case 41245 -> {
                OrdinaryResolventItems.useResolvent(pc, targetItem, item);
                yield ItemActionResult.STOP;
            }
            case 442455 -> {
                OrdinaryResolventItems.useDragonResolvent(pc, targetItem, item);
                yield ItemActionResult.STOP;
            }
            case 41303 -> LegacyGiftBoxes.open(use, 41303);
            case 46161 -> { serchdroplist2(pc, spellTargetId, item); yield ItemActionResult.COMPLETE; }
            case 41304 -> LegacyGiftBoxes.open(use, 41304);
            case 51093, 51094, 51095, 51096, 51097, 51098, 51099, 51100 -> { ClassChange.usePotion(pc, item); yield ItemActionResult.COMPLETE; }
            case 773355 -> { DurableBuffScrollItems.use(pc, item, itemId); yield ItemActionResult.STOP; }
            case 400246 -> CharacterResetConsumables.handleLevelBuff(use);
            case 410009 -> CharacterResetConsumables.handleFoodBuff(use);
            case 560025, 560027, 560029 -> { TelBook.clickItem(pc, itemId, teleportBookSlot, item); yield ItemActionResult.STOP; }
            case 560028, 4100135 -> { TelBook.clickItem(pc, itemId, teleportBookSlot, item); yield ItemActionResult.COMPLETE; }
            case 41248, 41249, 41250, 210070, 210071, 210072, 210086, 210096,
                    210105, 210106, 210107, 210108, 210109, 500212, 500213, 500214,
                    500215, 447012, 447013, 447014, 30022, 30023, 30024, 30025,
                    447015, 447016, 447017, 510216, 510217, 510218, 510219, 510220,
                    510221, 510222, 410172, 410173, 4100007, 4100008, 4100009, 4100010,
                    740, 741, 742, 743, 744, 745, 746, 750,
                    3000086, 3000087, 3000088, 3000351, 3000352, 42654, 755, 756,
                    757, 758, 759, 760, 761, 772, 773, 774,
                    775, 4100134, 4200010, 4200020, 4200030, 4200040, 4200050, 4200060,
                    950000, 950001, 950002, 950003, 950004, 950005, 950006, 950007,
                    950008, 950009, 950010, 950011, 950012, 950013, 950014, 950015,
                    950016, 950017, 950018, 950019, 4200051 -> { MagicDoll.useMagicDoll(pc, item); yield ItemActionResult.COMPLETE; }
            case 410014 -> { useNpcSpownWand(pc, 60001, item); yield ItemActionResult.STOP; }
            case 4100254 -> { use_bug_down_wand(pc, spellTargetId, item); yield ItemActionResult.COMPLETE; }
            case 46160 -> { useFieldObjectRemovalWand(pc, spellTargetId, item); yield ItemActionResult.COMPLETE; }
            case 46162 -> { useFieldObjectRemovalWand1(pc, spellTargetId, item); yield ItemActionResult.COMPLETE; }
            case 220012 -> MagicShieldItemEffect.매직실드(pc)
                    ? ItemActionResult.COMPLETE : ItemActionResult.STOP;
            case 994411 -> { 투견조작(pc, spellTargetId, item); yield ItemActionResult.COMPLETE; }
            case 44114 -> { 전투지휘봉(pc, spellTargetId, item); yield ItemActionResult.COMPLETE; }
            case 70721 -> CallAdvanceItemEffect.콜어드밴스(pc, spellTargetId, item)
                    ? ItemActionResult.COMPLETE : ItemActionResult.STOP;
            case 90088 -> { 비전텔레포트(pc, spellTargetX, spellTargetY); yield ItemActionResult.COMPLETE; }
            case 80888 -> { 이터니티(pc, spellTargetId, item); yield ItemActionResult.COMPLETE; }
            case 70888 -> ElvenStrikeItemEffect.엘븐스트라이크(pc, spellTargetId, item)
                    ? ItemActionResult.COMPLETE : ItemActionResult.STOP;
            case 875640444 -> CombatBuffConsumables.handleDragonSkill(use);
            case 707777 -> { 쉐도우(pc, spellTargetId, item); yield ItemActionResult.COMPLETE; }
            case 707779 -> AvengerItemEffect.어벤저(pc, spellTargetId, item)
                    ? ItemActionResult.COMPLETE : ItemActionResult.STOP;
            case 707781 -> DemolitionItemEffect.데몰리션(pc, spellTargetId, item)
                    ? ItemActionResult.COMPLETE : ItemActionResult.STOP;
            case 410015 -> { useNpcSpownWand(pc, 70030, item); yield ItemActionResult.STOP; }
            case 4000138, 4000139, 4000140, 4000141, 4000142, 4000143, 4000144 -> DragonBallConfirmationItems.requestConfirmation(use);
            case 41345 -> BlessingConsumables.handleHolyWater(use);
            case 41315 -> BlessingConsumables.handleHolyWaterOfEva(use);
            case 30055, 3000425, 711 -> { AreaDamageItems.use(pc, item, spellTargetId, itemId); yield ItemActionResult.STOP; }
            case 30071 -> { StarterEquipmentGiftBox.openStarterKit(pc, item); yield ItemActionResult.STOP; }
            case 700024 -> { BookmarkSlot.UseBookmarkItem(pc, item, itemId, false); yield ItemActionResult.COMPLETE; }
            case 3000065, 3000256, 3110509, 3000509, 4100446 -> { ItemEnchanterLoader.getInstance().do_enchant(pc, item, targetItem); yield ItemActionResult.COMPLETE; }
            case 5558 -> { 정상의가호(pc, item); yield ItemActionResult.COMPLETE; }
            case 55558 -> { 정상의가호2(pc, item); yield ItemActionResult.STOP; }
            case 30116 -> { PureShirtSoapExchange.exchangePureShirt(pc, item, targetItem); yield ItemActionResult.STOP; }
            case 560030, 560031, 560032, 560033, 40130, 140130, L1ItemId.SCROLL_OF_ENCHANT_WEAPON, L1ItemId.SCROLL_OF_ENCHANT_QUEST_WEAPON,
                    L1ItemId.SCROLL_OF_ENCHANT_FANTASY_WEAPON, L1ItemId.B_SCROLL_OF_ENCHANT_WEAPON, L1ItemId.C_SCROLL_OF_ENCHANT_WEAPON, L1ItemId.IVORYTOWER_WEAPON_SCROLL, L1ItemId.ENCHANT_WEAPONA, 210085, 210064, 724,
                    210065, 210066, 210067, 310064, 310065, 310066, 310067, 310068,
                    810003, 4100148, 127000, 68076, 30146, 30068 -> EnchantScrollActions.useWeapon(use);
            case L1ItemId.SCROLL_OF_ENCHANT_ARMOR, L1ItemId.SCROLL_OF_ENCHANT_ARMOR4, L1ItemId.B_SCROLL_OF_ENCHANT_ARMOR5, L1ItemId.C_SCROLL_OF_ENCHANT_ARMOR6, L1ItemId.SCROLL_OF_ENCHANT_FANTASY_ARMOR, L1ItemId.B_SCROLL_OF_ENCHANT_ARMOR, L1ItemId.C_SCROLL_OF_ENCHANT_ARMOR, L1ItemId.ENCHANT_ARMOR,
                    L1ItemId.Inadril_T_ScrollA, L1ItemId.Inadril_T_ScrollB, L1ItemId.Inadril_T_ScrollC, L1ItemId.Pure_white_Scroll, L1ItemId.Roomtis_Scroll, L1ItemId.IVORYTOWER_ARMOR_SCROLL, L1ItemId.IVORYTOWER_GKFFHDNLS, L1ItemId.Inadril_T_ScrollA1,
                    L1ItemId.Inadril_T_ScrollB2, L1ItemId.Inadril_T_ScrollB3, L1ItemId.ENCHANT_TEST1, L1ItemId.ENCHANT_TEST2, L1ItemId.ENCHANT_TEST3, L1ItemId.ENCHANT_TEST4, L1ItemId.ENCHANT_TEST5, L1ItemId.ENCHANT_TEST6,
                    L1ItemId.ENCHANT_TEST33, L1ItemId.ENCHANT_TEST11, L1ItemId.ENCHANT_TEST22, L1ItemId.ENCHANT_TEST44, L1ItemId.ENCHANT_TEST55, 7004, 40129, 140129,
                    210084, 3000100, 4000517, 5991, 3000517, 210068, 68077, 98077,
                    3000380, 30069, 810012, 810013, 30147 -> EnchantScrollActions.useArmor(use);
            default -> UnlistedItemRouter.use(use);
        };
    }
}
