#include "AlwaysCoopStrategy.h"

#include <string>

#include "Factory.h"
#include "Factory.cpp"

constexpr char kAlwaysCoopID[] = "coop";

namespace {
    Strategy *create(size_t orderNumber, TChoiceMatrix &history,
                     TScoreMap &scoreMap, TConfigs &configs) {
        return new AlwaysCoopStrategy(orderNumber, history, scoreMap, configs);
    }
}

bool coopB = Factory<Strategy, std::string, size_t, TChoiceMatrix &, TScoreMap &, TConfigs &>::
getInstance()->registerCreator(kAlwaysCoopID, create);

TChoice AlwaysCoopStrategy::getChoice() {
    return COOP;
}
