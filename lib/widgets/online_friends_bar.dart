// lib/widgets/online_friends_bar.dart
import 'package:flutter/material.dart';
import 'package:flutter_chat_demo/constants/constants.dart';
import 'package:flutter_chat_demo/pages/pages.dart';
import 'package:flutter_chat_demo/providers/providers.dart';
import 'package:provider/provider.dart';

class OnlineFriendsBar extends StatelessWidget {
  final String currentUserId;

  const OnlineFriendsBar({
    super.key,
    required this.currentUserId,
  });

  @override
  Widget build(BuildContext context) {
    final presenceProvider = context.read<UserPresenceProvider>();

    return Container(
      height: 100,
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 5,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: StreamBuilder<List<Map<String, dynamic>>>(
        stream: presenceProvider.getOnlineFriends(currentUserId),
        builder: (context, snapshot) {
          if (!snapshot.hasData) {
            return const Center(
              child: CircularProgressIndicator(
                color: ColorConstants.themeColor,
              ),
            );
          }

          final onlineFriends = snapshot.data!
              .where((user) => user['id'] != currentUserId)
              .toList();

          if (onlineFriends.isEmpty) {
            return Center(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.wifi_off,
                    color: ColorConstants.greyColor,
                    size: 20,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    'No friends online',
                    style: TextStyle(
                      color: ColorConstants.greyColor,
                      fontSize: 14,
                    ),
                  ),
                ],
              ),
            );
          }

          return ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
            itemCount: onlineFriends.length,
            itemBuilder: (context, index) {
              final friend = onlineFriends[index];
              return _buildOnlineFriendItem(context, friend);
            },
          );
        },
      ),
    );
  }

  Widget _buildOnlineFriendItem(
      BuildContext context, Map<String, dynamic> friend) {
    return GestureDetector(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => ChatPage(
              arguments: ChatPageArguments(
                peerId: friend['id'],
                peerAvatar: friend['photoUrl'],
                peerNickname: friend['nickname'],
              ),
            ),
          ),
        );
      },
      child: Container(
        width: 70,
        margin: const EdgeInsets.symmetric(horizontal: 4),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Stack(
              children: [
                // Avatar
                Container(
                  width: 60,
                  height: 60,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: ColorConstants.primaryColor,
                      width: 2,
                    ),
                  ),
                  child: ClipOval(
                    child: friend['photoUrl'].isNotEmpty
                        ? Image.network(
                            friend['photoUrl'],
                            fit: BoxFit.cover,
                            errorBuilder: (_, __, ___) => Icon(
                              Icons.account_circle,
                              size: 56,
                              color: ColorConstants.greyColor,
                            ),
                          )
                        : Icon(
                            Icons.account_circle,
                            size: 56,
                            color: ColorConstants.greyColor,
                          ),
                  ),
                ),

                // Online indicator
                Positioned(
                  right: 2,
                  bottom: 2,
                  child: Container(
                    width: 16,
                    height: 16,
                    decoration: BoxDecoration(
                      color: Colors.green,
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: Colors.white,
                        width: 2,
                      ),
                    ),
                  ),
                ),
              ],
            ),

            const SizedBox(height: 4),

            // Name
            Text(
              friend['nickname'].length > 8
                  ? '${friend['nickname'].substring(0, 8)}...'
                  : friend['nickname'],
              style: const TextStyle(
                fontSize: 12,
                color: ColorConstants.primaryColor,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
