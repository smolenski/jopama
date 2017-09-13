import argparse
import doctest
import sys
import subprocess
import time
import os
import random
from datetime import datetime

def gId2Name(gId):
    return format('jopamaTest_%d' % gId)

def id2ZKPortExt(id):
    return 10000 + 10 * id

def id2ZKPortInt1(id):
    return 10001 + 10 * id

def id2ZKPortInt2(id):
    return 10002 + 10 * id

def id2TPPort(id):
    return 10003 + 10 * id

def id2TCPort(id):
    return 10004 + 10 * id

def printIt(cmd, shell=None):
    print('%s' % cmd)

#subprocess.check_call=printIt

class Instance(object):
    def __init__(self, gId, clId, coId, ip, runTP, runTC):
        self.gId = gId
        self.clId = clId
        self.coId = coId
        self.ip = ip
        self.runTP = runTP
        self.runTC = runTC
    def __str__(self):
        return "gId: %d clId: %d coId: %d ip: %s runTP: %s runTC: %s" % (self.gId, self.clId, self.coId, self.ip, self.runTP, self.runTC)

class ConfigGenerator(object):
    def __init__(self, args):
        self.args=args
        self.dist=[]
    def generate(self):
        numTPOnIp={}
        numTCOnIp={}
        for ip in args.ips:
            numTPOnIp[ip]=0
            numTCOnIp[ip]=0    
        for cid in range(args.numClusters):
            gIdAndNumP=[]
            gIdAndNumC=[]
            for rid in range(args.clusterSize):
                gId = cid * args.clusterSize + rid
                ip = args.ips[gId % len(args.ips)]
                gIdAndNumP.append((gId, numTPOnIp[ip]))
                gIdAndNumC.append((gId, numTCOnIp[ip]))
            tpss=[a[0] for a in sorted(gIdAndNumP, key=lambda x: x[1])][:args.numTP]
            tcss=[a[0] for a in sorted(gIdAndNumC, key=lambda x: x[1])][:args.numTC]
            for rid in range(args.clusterSize):
                gId = cid * args.clusterSize + rid
                ip = args.ips[gId % len(args.ips)]
                if gId in tpss:
                    numTPOnIp[ip] = numTPOnIp[ip] + 1
                if gId in tcss:
                    numTCOnIp[ip] = numTCOnIp[ip] + 1
                inst = Instance(gId, cid, rid, ip, gId in tpss, gId in tcss)
                self.dist.append(inst)
        assert max(numTPOnIp.values()) - min(numTPOnIp.values()) <= 1
        assert max(numTCOnIp.values()) - min(numTCOnIp.values()) <= 1
    def getZKServersConf(self, gId):
        startId=gId - (gId % self.args.clusterSize)
        stopId=startId + self.args.clusterSize
        assert len(self.dist) >= stopId
        servers=[]
        for mId in range(startId, stopId):
            intPort1=None
            intPort2=None
            intPort1=id2ZKPortInt1(mId)
            intPort2=id2ZKPortInt2(mId)
            mInst = self.dist[mId]
            servers.append(mInst.ip + ':' + str(intPort1) + ':' + str(intPort2))
        return ','.join(servers)
    def getZKServerId(self, gId):
        return gId % self.args.clusterSize 

class TestRunner(object):
    def __init__(self, args, gen):
        self.args = args
        self.gen = gen
        self.dist = self.gen.dist
        self.start = None
        self.finish = None

    def rotateLogDir(self):
        print("Rotating log directory (not impl yet)")

    def getAddresses(self):
        clId2Member={}
        for i in range(self.args.numClusters):
            clId2Member[i]=[]
        for ins in self.dist:
            clId2Member[ins.clId].append(ins)
        addresses=[]
        for clId in sorted(clId2Member):
            for ins in clId2Member[clId]:
                addresses.append(format('%s:%d' % (ins.ip, id2ZKPortExt(ins.gId))))
        return ','.join(addresses)

    def startZooKeepers(self):
        for ins in self.dist:
            name=gId2Name(ins.gId)
            hostStorageDir=format("/var/jopamaTest/storage/%d/zookeeper" % ins.gId)
            hostLogsDir=format("/var/jopamaTest/logs/%d/zookeeper" % ins.gId)
            extPort=id2ZKPortExt(ins.gId)
            intPort1=id2ZKPortInt1(ins.gId)
            intPort2=id2ZKPortInt2(ins.gId)
            peers=self.gen.getZKServersConf(ins.gId)
            zkId=self.gen.getZKServerId(ins.gId)
            subprocess.check_call('mkdir -p %s' % hostStorageDir, shell=True)
            subprocess.check_call('mkdir -p %s' % hostLogsDir, shell=True)
            print("Starting ZooKeeper, ins: %d, extport: %d" % (ins.gId, extPort))
            subprocess.check_call(
                'docker run -d --name %s --net host -v %s:/var/jopamaTest/storage -v %s:/var/jopamaTest/logs -p %d -p %d -p %d zookeeper %s %d %d'
                %
                (
                    name,
                    hostStorageDir,
                    hostLogsDir,
                    extPort,
                    intPort1,
                    intPort2,
                    peers,
                    extPort,
                    zkId
                ),
                shell=True
            )

    def zkCli(self, server, cmd):
        name=str(random.getrandbits(100))
        output=subprocess.check_output(
            'docker run --name %s --network host --entrypoint /opt/ZooKeeper/zookeeper-3.4.9/bin/zkCli.sh zookeeper -server %s %s'
            %
            (
                name,
                server,
                cmd
            ),
            shell=True
        )
        subprocess.check_output('docker rm %s' % (name), shell=True)
        return output

    def stopZooKeepers(self):
        for ins in self.dist:
            name=gId2Name(ins.gId)
            subprocess.check_call('docker kill %s' % name, shell=True)
            subprocess.check_call('docker rm %s' % name, shell=True)

    def startTPs(self):
        for instance in self.dist:
            if instance.runTP:
                print("Starting TP %s:%d" % (instance.ip, id2TPPort(instance.gId)))

    def stopTPs(self):
        for instance in self.dist:
            if instance.runTP:
                print("Stopping TP %s:%d" % (instance.ip, id2TPPort(instance.gId)))

        
    def startTCs(self):
        for instance in self.dist:
            if instance.runTC:
                print("Starting TC %s:%d" % (instance.ip, id2TCPort(instance.gId)))

    def stopTCs(self):
        for instance in self.dist:
            if instance.runTC:
                print("Stopping TC %s:%d" % (instance.ip, id2TCPort(instance.gId)))

    def createComponents(self):
        print("Running CC")
        name='CC'
        hostLogsDir=format("/var/jopamaTest/logs/%s" % name)
        addresses=self.getAddresses()
        subprocess.check_call('mkdir -p %s' % hostLogsDir, shell=True)
        print("Starting CC")
        subprocess.check_call(
            'docker run --name %s --net host -v %s:/var/jopamaTest/logs jopama pl.rodia.jopama.integration.zookeeper.ZooKeeperComponentCreator CC %s %d %d %d'
            %
            (
                name,
                hostLogsDir,
                addresses,
                self.args.numClusters,
                self.args.firstComp,
                self.args.numComp    
            ),
            shell=True
        )
        subprocess.check_call(
            'docker rm %s' % (name),
            shell=True
        )

    def runTV(self):
        print("Running TV")
        name='TV'
        hostLogsDir=format("/var/jopamaTest/logs/%s" % name)
        addresses=self.getAddresses()
        subprocess.check_call('mkdir -p %s' % hostLogsDir, shell=True)
        print("Starting TV")
        subprocess.check_call(
            'docker run --name %s --net host -v %s:/var/jopamaTest/logs jopama pl.rodia.jopama.integration.zookeeper.ZooKeeperTestVerifier TV %s %d %d %d'
            %
            (
                name,
                hostLogsDir,
                addresses,
                self.args.numClusters,
                self.args.firstComp,
                self.args.numComp  
            ),
            shell=True
        )
        subprocess.check_call(
            'docker rm %s' % (name),
            shell=True
        )

    def waitForReady(self):
        print("Waiting for ready")

    def waitForDone(self):
        print("Waiting for done")

    def triggerStart(self):
        print("Triggering start")

    def triggerFinish(self):
        print("Triggering finish")

    def waitDesiredDuration(self):
        print("Wait desired duration")
        time.sleep(5)

    def performLs(self):
        print('Addresses: %s' % (self.getAddresses()))
        print('Performing ls')
        assert len(self.dist) > 0
        ins=self.dist[0]
        server=format('%s:%d' % (ins.ip, id2ZKPortExt(ins.gId)))
        output=self.zkCli(server, 'ls /')
        lines=output.splitlines()
        assert len(lines) > 0
        print('zoo ls result: %s' % lines[-1])

    def prepareDirectories(self):
        time.sleep(2)
        clusters=[]
        for i in range(self.args.numClusters):
            clusters.append([])
        for ins in self.dist:
           clusters[ins.clId].append(ins) 
        for i in range(self.args.numClusters):
            assert len(clusters[i]) > 0
            ins=clusters[i][0]
            server=format('%s:%d' % (ins.ip, id2ZKPortExt(ins.gId)))
            time.sleep(5)
            self.zkCli(server, 'create /Transactions a')
            self.zkCli(server, 'create /Components a')
            self.zkCli(server, 'create /StartFinish a')
            self.zkCli(server, 'create /TP_READY a')
            self.zkCli(server, 'create /TP_DONE a')
            self.zkCli(server, 'create /TC_READY a')
            self.zkCli(server, 'create /TC_DONE a')

    def getProcessedTransactionsNum(self):
        return 1000

    def printResult(self):
        durSec = (self.finish - self.start).seconds
        num = self.getProcessedTransactionsNum()
        print("Performance num: %d dur: %d speed: %d (T/s)" % (num, durSec, float(num) / durSec))
        
    def run(self):
        self.rotateLogDir()
        self.startZooKeepers()
        self.startTPs()
        self.startTCs()
        self.prepareDirectories()
        self.createComponents()
        self.waitForReady()
        self.start = datetime.now()
        self.triggerStart()
        self.waitDesiredDuration()
        self.performLs()
        self.triggerFinish()
        self.waitForDone()
        self.finish = datetime.now()
        self.runTV()
        self.printResult()
        self.stopTCs()
        self.stopTPs()
        self.stopZooKeepers()

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Generate jopama docker cluster configuration')
    parser.add_argument('-ips', type=str, nargs='+', required=True, dest='ips')
    parser.add_argument('-numClusters', type=int, required=True, dest='numClusters')
    parser.add_argument('-clusterSize', type=int, required=True, dest='clusterSize')
    parser.add_argument('-numTP', type=int, required=True, dest='numTP', help='Num transaction processors per cluster')
    parser.add_argument('-numTC', type=int, required=True, dest='numTC', help='Num transaction creators per cluster')
    parser.add_argument('-firstComp', type=int, required=True, dest='firstComp', help="First component")
    parser.add_argument('-numComp', type=int, required=True, dest='numComp', help="Number of components")
    parser.add_argument('-compsInTra', type=int, required=True, dest='compsInTra', help="Components in transaction")
    parser.add_argument('-outForTC', type=int, required=True, dest='outcre', help="Outstanding transactions for creator")
    parser.add_argument('-outForTP', type=int, required=True, dest='outForTP', help="Outstanding transactions for processor")
    parser.add_argument('-doctest', action='store_true', dest='doctest', help="Run doctests")
    args = parser.parse_args()
    if args.doctest:
        doctest.testmod()
        sys.exit(0) 
    if (args.numClusters * args.clusterSize) % len(args.ips) != 0:
        print('Arguments, numClusters * clusterSize should be multiplication of num ips, numClusters: %d, clusterSize: %d num ips: %d' % (args.numClusters, args.clusterSize, len(args.ips)))
        sys.exit(1)
    assert args.numTP > 0
    assert args.numTP <= args.clusterSize
    assert args.numTC > 0
    assert args.numTC <= args.clusterSize
    print("args: %s" % args)
    gen = ConfigGenerator(args)
    gen.generate()
    print('DIST BEGIN')
    for inst in gen.dist:
        print('%s' % str(inst))
    print('DIST END')
    test = TestRunner(args, gen)
    test.run()
    sys.exit(0)
