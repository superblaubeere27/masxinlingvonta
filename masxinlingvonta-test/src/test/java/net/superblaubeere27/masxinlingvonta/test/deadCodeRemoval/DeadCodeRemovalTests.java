package net.superblaubeere27.masxinlingvonta.test.deadCodeRemoval;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;

import java.util.ArrayList;
import java.util.Iterator;

public class DeadCodeRemovalTests {
    public static void main(String[] args) throws InterruptedException {
    }

    @Outsource
    private static Object test(ArrayList<Object> objects) {
//        Arrays.stream(objects).forEach(x -> {
//            x.hashCode();
//        });
        for (Iterator<Object> iterator = objects.iterator(); iterator.hasNext(); ) {
            Object object = iterator.next();
            if (object != null) {
                return object;
            }
        }

        return null;
    }

}
