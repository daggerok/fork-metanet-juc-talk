package com.hazelcast.juctalk;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.YamlClientConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicReference;
import com.hazelcast.core.ICountDownLatch;
import com.hazelcast.cp.CPSubsystem;
import com.hazelcast.logging.ILogger;

import java.util.Random;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static com.hazelcast.juctalk.PrimitiveNames.NOTIFIER_LATCH_NAME;
import static com.hazelcast.juctalk.PrimitiveNames.PHOTO_REF_NAME;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This class starts a pet owner.
 * <p>
 * A pet owner periodically creates a pet {@link Photo} and publishes it
 * through a linearizable {@link IAtomicReference} instance. It also counts
 * down the {@link PrimitiveNames#NOTIFIER_LATCH_NAME} latch to notify
 * the parties that are reading published {@link Photo} objects.
 */
public class RunPetOwner {

    public static void main(String[] args) {
        String pet = getPet(args);

        ClientConfig config = new YamlClientConfigBuilder().build();
        HazelcastInstance client = HazelcastClient.newHazelcastClient(config);

        ILogger logger = client.getLoggingService().getLogger(RunPetOwner.class);
        String address = client.getLocalEndpoint().getSocketAddress().toString();
        CPSubsystem cpSubsystem = client.getCPSubsystem();

        IAtomicReference<Photo> photoRef = cpSubsystem.getAtomicReference(PHOTO_REF_NAME);
        ICountDownLatch notifier = cpSubsystem.getCountDownLatch(NOTIFIER_LATCH_NAME);

        notifier.trySetCount(1);
        Random random = new Random();

        while (true) {
            Photo currentPhoto = photoRef.get();
            int nextVersion = currentPhoto != null ? currentPhoto.getId() + 1 : 1;
            int petIndex = 1 + random.nextInt(15);
            Photo newPhoto = new Photo(nextVersion, pet + petIndex + ".png");
            photoRef.set(newPhoto);

            logger.info("PetOwner<" + address + "> published " + newPhoto);

            notifier.countDown();
            notifier.trySetCount(1);

            sleepUninterruptibly(2000 + random.nextInt(1000), MILLISECONDS);
        }
    }

    public static String getPet(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("You must provide a single argument: cat|dog");
        }

        String pet = args[0].trim().toLowerCase();
        if (!(pet.equals("cat") || pet.equals("dog"))) {
            throw new IllegalArgumentException("You must provide a single argument: cat|dog");
        }
        return pet;
    }

}