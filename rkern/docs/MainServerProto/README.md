# MainServer Proto map

All dedicated Protobuf Java classes live below the Protocol/MainServer tree.
Proto: CSProto, ProtoMsg, Proto, ProtoReader, ProtoWriter, and packet writers.
Proto/Stream: ProtoInputStream, ProtoOutputStream, WireFormat.
Proto/Dispatch: CS_ExtendedProto incoming handler.
Proto/Adapter: SC_NOTIFICATION_MESSAGE compatibility adapter.
Indun/Proto, Inventory/Proto, Companion/Proto, Pledge/Proto:
functional message classes.
PlaySupport: existing PSS implementation and PlaySupport/Proto native window.
MainServer/Proto/Attend, Craft, MonsterBook, Ranker: packet helper groups.

The 12 proto schema texts in schemas/ were reconstructed from client binary
FileDescriptorProto data. They are not original NC-authored source.

Unrelated legacy game packet handlers in network/core/(cs,sc)/packet remain
in place. CIO transport, game logic, message IDs and wire encoding are not
modified. Package grouping is a Java organization decision.
