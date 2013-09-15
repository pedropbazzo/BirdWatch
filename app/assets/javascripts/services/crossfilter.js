//'use strict';

// crossfilter service
angular.module('birdwatch.services').service('cf', function (utils) {
    var exports = {};

    // crossfilter object: browser side analytics library, holds array type data (w/incremental updates).
    // dimensions are fast queries on data, e.g. view sorted by followers_count or retweet_count of the original message
    var cf = crossfilter([]);
    var tweetIdDim   = cf.dimension(function(t) { return t.id; });
    var followersDim = cf.dimension(function(t) { return t.user.followers_count; });
    var retweetsDim  = cf.dimension(function(t) {
        if (t.hasOwnProperty("retweeted_status")) { return t.retweeted_status.retweet_count; }
        else return 0;
    });

    // freeze imposes filter on crossfilter that only shows anything older than and including the latest
    // tweet at the time of calling freeze. Accordingly unfreeze clears the filter
    exports.freeze    = function() { tweetIdDim.filter([0, tweetIdDim.top(1)[0].id]); };
    exports.unfreeze  = function() { tweetIdDim.filterAll(); };

    exports.add       = function(data)     { cf.add(data); };                            // add new items, as array
    exports.clear     = function()         { cf.remove(); };                             // reset crossfilter
    exports.noItems   = function()         { return cf.size(); };                        // crossfilter size total
    exports.numPages  = function(pageSize) { return Math.ceil(cf.size() / pageSize); };  // number of pages

    // mapper functions
    var originalTweet = function(t) { return utils.formatTweet(t.retweeted_status); };   // returns original tweet
    var tweetId       = function(t) { return t.id; };                                    // returns tweet id

    // predicates
    var retweeted     = function(t) { return t.hasOwnProperty("retweeted_status"); };

    // deliver tweets for current page. fetches all tweets up to the current page,
    // throws tweets for previous pages away.
    exports.tweetPage = function(currentPage, pageSize, order, live) {
        return _.rest(fetchTweets(currentPage * pageSize, order), (currentPage - 1) * pageSize);
    };

    // fetch tweets from crossfilter dimension associated with particular sort order up to the current page,
    // potentially mapped and filtered
    var fetchTweets = function(pageSize, order) {
      if      (order === "latest")    { return tweetIdDim.top(pageSize); }    // latest: desc order of tweets by ID
      else if (order === "followers") { return followersDim.top(pageSize);}   // desc order of tweets by followers
      else if (order === "retweets") {  // descending order of tweets by total retweets of original message
          return _.first(               // filtered to be unique, would appear for each retweet in window otherwise
              _.uniq(retweetsDim.top(cf.size()).filter(retweeted).map(originalTweet), false, tweetId), pageSize);
      }
      else { return []; }
    };

    return exports;
});
