// lib/widgets/online_friends_bar.dart - OVERFLOW FIXED
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
              child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: ColorConstants.themeColor,
                ),
              ),
            );
          }

          final onlineFriends = snapshot.data!
              .where((user) => user['id'] != currentUserId)
              .toList();

          if (onlineFriends.isEmpty) {
            return Center(
              child: Row(
                mainAxisSize: MainAxisSize.min,
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

          // ✅ FIX: Use ListView with proper constraints
          return ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
            itemCount: onlineFriends.length,
            separatorBuilder: (_, __) => const SizedBox(width: 8),
            itemBuilder: (context, index) {
              return _OnlineFriendItem(
                key: ValueKey(onlineFriends[index]['id']),
                friend: onlineFriends[index],
              );
            },
          );
        },
      ),
    );
  }
}

// ✅ FIX: Optimized friend item widget
class _OnlineFriendItem extends StatelessWidget {
  final Map<String, dynamic> friend;

  const _OnlineFriendItem({
    super.key,
    required this.friend,
  });

  @override
  Widget build(BuildContext context) {
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
      child: SizedBox(
        width: 70, // ✅ FIX: Fixed width to prevent overflow
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Avatar with status
            SizedBox(
              width: 60,
              height: 60,
              child: Stack(
                children: [
                  // Avatar
                  Hero(
                    tag: 'avatar_${friend['id']}',
                    child: Container(
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
                        child: friend['photoUrl'].toString().isNotEmpty
                            ? Image.network(
                                friend['photoUrl'],
                                fit: BoxFit.cover,
                                cacheWidth: 60,
                                cacheHeight: 60,
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
            ),

            const SizedBox(height: 4),

            // Name with proper text overflow handling
            SizedBox(
              width: 70, // ✅ FIX: Match parent width
              child: Text(
                friend['nickname'].toString(),
                style: const TextStyle(
                  fontSize: 12,
                  color: ColorConstants.primaryColor,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
