package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.metadata.DBInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Identifier comparison for every index planning path. SQL always quotes names. */
final class IndexIdentity {
    private final Comparator<String> identifiers;

    IndexIdentity(DBInfo.Type databaseType) {
        identifiers = databaseType == DBInfo.Type.POSTGRESQL
                ? Comparator.naturalOrder() : String.CASE_INSENSITIVE_ORDER;
    }

    Comparator<String> comparator() {
        return identifiers;
    }

    boolean sameIdentifier(String left, String right) {
        return left != null && right != null && identifiers.compare(left, right) == 0;
    }

    boolean sameColumns(List<String> left, List<String> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!sameIdentifier(left.get(i), right.get(i))) return false;
        }
        return true;
    }

    boolean sameColumnSet(List<String> left, List<String> right) {
        Set<String> leftSet = new TreeSet<>(identifiers);
        Set<String> rightSet = new TreeSet<>(identifiers);
        leftSet.addAll(left);
        rightSet.addAll(right);
        return leftSet.equals(rightSet);
    }
}
